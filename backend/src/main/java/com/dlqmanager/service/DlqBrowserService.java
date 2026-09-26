package com.dlqmanager.service;

import com.dlqmanager.model.dto.DlqMessageDto;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.repository.DlqTopicRepository;
import com.dlqmanager.repository.ReplayMessageRepository;
import com.dlqmanager.util.DlqHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;

/**
 * DLQ Browser Service
 *
 * Purpose: Read messages from DLQ topics on-demand (not continuously)
 *
 * Key Responsibilities:
 * 1. Create Kafka Consumer for browsing
 * 2. Implement pagination across ALL partitions of the topic
 * 3. Read N messages from a DLQ topic
 * 4. Convert raw Kafka messages to DlqMessageDto
 * 5. Handle errors gracefully
 *
 * How pagination works:
 * - A topic is split into partitions, each with its own offsets
 * - Kafka deletes old messages (retention), so a partition rarely starts at offset 0
 * - We treat the topic as partition 0's messages, then partition 1's, and so on,
 *   using each partition's real beginning offset. That lets us jump straight
 *   to any page without reading the pages before it.
 *
 * Why a separate service?
 * - Controller handles HTTP concerns
 * - Service handles Kafka business logic
 * - Easy to test and modify independently
 */
@Service
@Slf4j
public class DlqBrowserService {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);
    private static final int MAX_EMPTY_POLLS = 3;

    /**
     * Upper limit for the error breakdown scan, so one huge DLQ can't hang the page.
     */
    private static final long MAX_BREAKDOWN_MESSAGES = 100_000;

    private final DlqTopicRepository dlqTopicRepository;
    private final KafkaConfigService kafkaConfigService;
    private final ReplayMessageRepository replayMessageRepository;

    public DlqBrowserService(
            DlqTopicRepository dlqTopicRepository,
            KafkaConfigService kafkaConfigService,
            ReplayMessageRepository replayMessageRepository
    ) {
        this.dlqTopicRepository = dlqTopicRepository;
        this.kafkaConfigService = kafkaConfigService;
        this.replayMessageRepository = replayMessageRepository;
    }

    /**
     * Message counts for a DLQ topic
     *
     * @param total        messages currently stored in Kafka (all partitions)
     * @param replayed     of those, how many were already replayed successfully
     * @param pending      messages still waiting to be handled (total - replayed)
     * @param endOffsetSum sum of end offsets - only grows, used to measure new arrivals over time
     */
    public record MessageCounts(long total, long replayed, long pending, long endOffsetSum) {
    }

    /**
     * Get messages from a DLQ topic with pagination
     *
     * Flow:
     * 1. Look up DLQ topic in database (get topic name)
     * 2. Find all partitions and their beginning/end offsets
     * 3. Work out which partition + offset the requested page starts at
     * 4. Read messages from there (moving on to the next partition if needed)
     * 5. Convert to DTOs and mark the ones already replayed
     *
     * @param dlqTopicId UUID of the DLQ topic in our database
     * @param page Page number (1-based)
     * @param size Number of messages per page
     * @return List of messages
     */
    public List<DlqMessageDto> getMessages(UUID dlqTopicId, int page, int size) {
        log.info("Fetching messages for DLQ topic ID: {}, page: {}, size: {}", dlqTopicId, page, size);

        DlqTopic dlqTopic = findDlqTopic(dlqTopicId);
        String topicName = dlqTopic.getDlqTopicName();
        Map<String, LocalDateTime> replayedOffsets = replayMessageRepository.findReplayedOffsets(dlqTopicId);

        List<DlqMessageDto> messages = new ArrayList<>();
        long toSkip = (long) (page - 1) * size;

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            List<TopicPartition> partitions = getPartitions(consumer, topicName);
            if (partitions.isEmpty()) {
                log.warn("Topic {} has no partitions (does it exist?)", topicName);
                return messages;
            }

            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            for (TopicPartition partition : partitions) {
                long begin = beginningOffsets.getOrDefault(partition, 0L);
                long end = endOffsets.getOrDefault(partition, 0L);
                long available = end - begin;

                if (available <= 0) {
                    continue;
                }

                // Whole partition is before the requested page - skip it without reading
                if (toSkip >= available) {
                    toSkip -= available;
                    continue;
                }

                long startOffset = begin + toSkip;
                toSkip = 0;

                int stillNeeded = size - messages.size();
                readRange(consumer, partition, startOffset, end, stillNeeded, record -> {
                    DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record, dlqTopic.getErrorFieldPath());
                    markReplayed(dto, replayedOffsets);
                    messages.add(dto);
                });

                if (messages.size() >= size) {
                    break;
                }
            }

            log.info("Successfully fetched {} messages from {} partition(s)", messages.size(), partitions.size());

        } catch (Exception e) {
            log.error("Error fetching messages from Kafka", e);
            throw new RuntimeException("Failed to fetch messages from topic: " + topicName, e);
        }

        return messages;
    }

    /**
     * Get total message count in a DLQ topic (all partitions)
     *
     * This is useful for pagination:
     * - Frontend needs to know total count to show "Page 1 of 5"
     * - For each partition: end offset - beginning offset
     *
     * @param dlqTopicId UUID of the DLQ topic
     * @return Total number of messages currently stored in the topic
     */
    public long getMessageCount(UUID dlqTopicId) {
        return getMessageCounts(dlqTopicId).total();
    }

    /**
     * Get total, replayed and pending counts for a DLQ topic
     *
     * Replayed messages stay in the DLQ (Kafka is append-only), so "total" never
     * goes down after a replay. "pending" is the number people actually care about:
     * messages that are still waiting to be fixed or replayed.
     *
     * @param dlqTopicId UUID of the DLQ topic
     * @return counts for the topic
     */
    public MessageCounts getMessageCounts(UUID dlqTopicId) {
        log.debug("Getting message counts for DLQ topic ID: {}", dlqTopicId);

        DlqTopic dlqTopic = findDlqTopic(dlqTopicId);
        String topicName = dlqTopic.getDlqTopicName();

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            List<TopicPartition> partitions = getPartitions(consumer, topicName);
            if (partitions.isEmpty()) {
                return new MessageCounts(0, 0, 0, 0);
            }

            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            long total = 0;
            long endOffsetSum = 0;
            for (TopicPartition partition : partitions) {
                long end = endOffsets.getOrDefault(partition, 0L);
                total += end - beginningOffsets.getOrDefault(partition, 0L);
                endOffsetSum += end;
            }

            // Only count replayed messages that are still stored in Kafka
            // (older ones were already removed by retention and are not part of "total")
            long replayed = 0;
            for (String key : replayMessageRepository.findReplayedOffsets(dlqTopicId).keySet()) {
                String[] parts = key.split(":");
                TopicPartition partition = new TopicPartition(topicName, Integer.parseInt(parts[0]));
                long offset = Long.parseLong(parts[1]);
                Long begin = beginningOffsets.get(partition);
                Long end = endOffsets.get(partition);
                if (begin != null && end != null && offset >= begin && offset < end) {
                    replayed++;
                }
            }

            long pending = Math.max(0, total - replayed);

            log.debug("Topic: {}, total: {}, replayed: {}, pending: {}", topicName, total, replayed, pending);
            return new MessageCounts(total, replayed, pending, endOffsetSum);

        } catch (Exception e) {
            log.error("Error getting message count", e);
            throw new RuntimeException("Failed to get message count for topic: " + topicName, e);
        }
    }

    /**
     * Get error breakdown statistics for a DLQ topic
     *
     * Purpose: Analyze all messages in the DLQ and group them by error type
     *
     * This helps answer questions like:
     * - What are the most common errors?
     * - What percentage of failures are due to DB timeouts vs validation errors?
     * - Should we prioritize fixing error type A or B?
     *
     * How it works:
     * 1. Read messages from every partition (not paginated)
     * 2. Work out the error type (custom X-* headers, Spring Kafka headers,
     *    Kafka Connect headers, or the topic's errorFieldPath in the payload)
     * 3. Count occurrences of each error type
     * 4. Return Map of errorType -> count
     *
     * Note: Stops after MAX_BREAKDOWN_MESSAGES so very large DLQs stay responsive.
     *
     * @param dlqTopicId UUID of the DLQ topic
     * @return Map where key = error type, value = count of messages with that error
     */
    public Map<String, Long> getErrorBreakdown(UUID dlqTopicId) {
        log.info("Getting error breakdown for DLQ topic ID: {}", dlqTopicId);

        DlqTopic dlqTopic = findDlqTopic(dlqTopicId);
        String topicName = dlqTopic.getDlqTopicName();
        String errorFieldPath = dlqTopic.getErrorFieldPath();

        Map<String, Long> errorCounts = new HashMap<>();
        long[] totalMessagesRead = {0};

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            List<TopicPartition> partitions = getPartitions(consumer, topicName);
            if (partitions.isEmpty()) {
                return errorCounts;
            }

            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            for (TopicPartition partition : partitions) {
                long remaining = MAX_BREAKDOWN_MESSAGES - totalMessagesRead[0];
                if (remaining <= 0) {
                    log.warn("Error breakdown for {} stopped at {} messages", topicName, MAX_BREAKDOWN_MESSAGES);
                    break;
                }

                long begin = beginningOffsets.getOrDefault(partition, 0L);
                long end = endOffsets.getOrDefault(partition, 0L);

                readRange(consumer, partition, begin, end, remaining, record -> {
                    Map<String, String> headers = DlqHeaders.toMap(record.headers());
                    String errorType = DlqHeaders.resolveErrorType(headers, record.value(), errorFieldPath);
                    errorCounts.merge(errorType, 1L, Long::sum);
                    totalMessagesRead[0]++;
                });
            }

            log.info("Read {} messages from topic {}. Found {} distinct error types.",
                    totalMessagesRead[0], topicName, errorCounts.size());

        } catch (Exception e) {
            log.error("Error getting error breakdown", e);
            throw new RuntimeException("Failed to get error breakdown for topic: " + topicName, e);
        }

        return errorCounts;
    }

    /**
     * Read messages from one partition, from startOffset up to (not including) endOffset
     *
     * @param consumer    consumer to use (will be re-assigned to this partition)
     * @param partition   partition to read
     * @param startOffset first offset to read
     * @param endOffset   stop before this offset (the end offset captured when we started)
     * @param limit       maximum number of messages to hand to the handler
     * @param handler     called for every message read
     */
    private void readRange(KafkaConsumer<String, String> consumer,
                           TopicPartition partition,
                           long startOffset,
                           long endOffset,
                           long limit,
                           Consumer<ConsumerRecord<String, String>> handler) {
        if (limit <= 0 || startOffset >= endOffset) {
            return;
        }

        consumer.assign(Collections.singletonList(partition));
        consumer.seek(partition, startOffset);

        long read = 0;
        int emptyPolls = 0;

        while (read < limit && emptyPolls < MAX_EMPTY_POLLS && consumer.position(partition) < endOffset) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);

            if (records.isEmpty()) {
                emptyPolls++;
                continue;
            }
            emptyPolls = 0;

            for (ConsumerRecord<String, String> record : records.records(partition)) {
                if (record.offset() >= endOffset || read >= limit) {
                    return;
                }
                handler.accept(record);
                read++;
            }
        }
    }

    /**
     * Get all partitions of a topic, sorted by partition number
     * (sorting keeps page order stable between requests)
     */
    private List<TopicPartition> getPartitions(KafkaConsumer<String, String> consumer, String topicName) {
        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topicName);
        if (partitionInfos == null) {
            return Collections.emptyList();
        }
        return partitionInfos.stream()
                .map(info -> new TopicPartition(info.topic(), info.partition()))
                .sorted(Comparator.comparingInt(TopicPartition::partition))
                .toList();
    }

    private void markReplayed(DlqMessageDto dto, Map<String, LocalDateTime> replayedOffsets) {
        String key = ReplayMessageRepository.offsetKey(dto.getPartition(), dto.getOffset());
        if (replayedOffsets.containsKey(key)) {
            dto.setReplayed(true);
            LocalDateTime replayedAt = replayedOffsets.get(key);
            // The app runs in UTC (see DlqManagerApplication), so send an ISO instant the browser can convert
            dto.setReplayedAt(replayedAt != null ? replayedAt.toInstant(ZoneOffset.UTC).toString() : null);
        }
    }

    /**
     * IllegalArgumentException is mapped to 404 Not Found by DlqTopicController
     */
    private DlqTopic findDlqTopic(UUID dlqTopicId) {
        return dlqTopicRepository.findById(dlqTopicId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ topic not found: " + dlqTopicId));
    }

    /**
     * Create a Kafka Consumer for browsing
     *
     * Key configurations:
     * - bootstrap.servers: Where to connect (read from Settings every time)
     * - group.id: Consumer group name (separate from real consumers!)
     * - enable.auto.commit: false (we're just reading, not processing)
     * - allow.auto.create.topics: false (looking at a topic must never create it)
     * - auto.offset.reset: earliest (start from beginning if no offset)
     * - key/value deserializers: Convert bytes back to Strings
     *
     * Why a unique group ID?
     * - We don't want to interfere with actual message processing
     * - Our browsing shouldn't affect consumer offsets of real apps
     */
    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigService.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-manager-browser-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");  // We're just browsing, don't commit offsets
        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // Start from beginning if no offset

        return new KafkaConsumer<>(props);
    }
}
