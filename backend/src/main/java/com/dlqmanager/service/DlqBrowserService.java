package com.dlqmanager.service;

import com.dlqmanager.model.dto.DlqMessageDto;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.repository.DlqTopicRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * DLQ Browser Service
 *
 * Purpose: Read messages from DLQ topics on-demand (not continuously)
 *
 * Key Responsibilities:
 * 1. Create Kafka Consumer for browsing
 * 2. Implement pagination (seek to specific offset)
 * 3. Read N messages from a DLQ topic
 * 4. Convert raw Kafka messages to DlqMessageDto
 * 5. Handle errors gracefully
 *
 * Why a separate service?
 * - Controller handles HTTP concerns
 * - Service handles Kafka business logic
 * - Easy to test and modify independently
 */
@Service
@Slf4j
public class DlqBrowserService {

    private final DlqTopicRepository dlqTopicRepository;
    private final String bootstrapServers;

    public DlqBrowserService(
            DlqTopicRepository dlqTopicRepository,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        this.dlqTopicRepository = dlqTopicRepository;
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * Get messages from a DLQ topic with pagination
     *
     * Flow:
     * 1. Look up DLQ topic in database (get topic name)
     * 2. Create Kafka Consumer
     * 3. Calculate starting offset based on page number
     * 4. Seek to that offset
     * 5. Poll messages
     * 6. Convert to DTOs
     * 7. Return list
     *
     * @param dlqTopicId UUID of the DLQ topic in our database
     * @param page Page number (1-based)
     * @param size Number of messages per page
     * @return List of messages
     */
    public List<DlqMessageDto> getMessages(UUID dlqTopicId, int page, int size) {
        log.info("Fetching messages for DLQ topic ID: {}, page: {}, size: {}", dlqTopicId, page, size);

        // Step 1: Look up DLQ topic in database
        DlqTopic dlqTopic = dlqTopicRepository.findById(dlqTopicId)
                .orElseThrow(() -> new RuntimeException("DLQ topic not found: " + dlqTopicId));

        String topicName = dlqTopic.getDlqTopicName();
        log.info("Topic name: {}", topicName);

        // Step 2: Create Kafka Consumer
        KafkaConsumer<String, String> consumer = createConsumer();

        List<DlqMessageDto> messages = new ArrayList<>();

        try {
            // Step 3: Get topic partitions
            // For MVP, we'll read from partition 0 only
            // Future: Read from all partitions and merge
            TopicPartition partition0 = new TopicPartition(topicName, 0);

            // Assign consumer to this partition
            consumer.assign(Collections.singletonList(partition0));

            // Step 4: Calculate starting offset
            // Page 1 → offset 0
            // Page 2 → offset 10 (if size=10)
            // Page 3 → offset 20
            long startOffset = (long) (page - 1) * size;
            log.info("Seeking to offset: {}", startOffset);

            // Step 5: Seek to the calculated offset
            consumer.seek(partition0, startOffset);

            // Step 6: Poll messages
            // We'll poll multiple times to ensure we get enough messages
            int messagesCollected = 0;
            int maxPolls = 10;  // Prevent infinite loop
            int pollCount = 0;

            while (messagesCollected < size && pollCount < maxPolls) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                pollCount++;

                for (ConsumerRecord<String, String> record : records) {
                    if (messagesCollected >= size) {
                        break;  // We have enough messages
                    }

                    // Step 7: Convert to DTO
                    DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record);
                    messages.add(dto);
                    messagesCollected++;
                }

                if (records.isEmpty()) {
                    log.info("No more messages available. Collected: {}", messagesCollected);
                    break;
                }
            }

            log.info("Successfully fetched {} messages", messages.size());

        } catch (Exception e) {
            log.error("Error fetching messages from Kafka", e);
            throw new RuntimeException("Failed to fetch messages from topic: " + topicName, e);
        } finally {
            // IMPORTANT: Always close the consumer to release resources
            consumer.close();
            log.info("Kafka consumer closed");
        }

        return messages;
    }

    /**
     * Get total message count in a DLQ topic
     *
     * This is useful for pagination:
     * - Frontend needs to know total count to show "Page 1 of 5"
     * - We seek to the end of partition to get the latest offset
     *
     * @param dlqTopicId UUID of the DLQ topic
     * @return Total number of messages in partition 0
     */
    public long getMessageCount(UUID dlqTopicId) {
        log.info("Getting message count for DLQ topic ID: {}", dlqTopicId);

        DlqTopic dlqTopic = dlqTopicRepository.findById(dlqTopicId)
                .orElseThrow(() -> new RuntimeException("DLQ topic not found: " + dlqTopicId));

        String topicName = dlqTopic.getDlqTopicName();

        KafkaConsumer<String, String> consumer = createConsumer();

        try {
            TopicPartition partition0 = new TopicPartition(topicName, 0);
            consumer.assign(Collections.singletonList(partition0));

            // Seek to beginning to get earliest offset
            consumer.seekToBeginning(Collections.singletonList(partition0));
            long beginningOffset = consumer.position(partition0);

            // Seek to end to get latest offset
            consumer.seekToEnd(Collections.singletonList(partition0));
            long endOffset = consumer.position(partition0);

            // Total messages = end offset - beginning offset
            long totalMessages = endOffset - beginningOffset;

            log.info("Topic: {}, Beginning offset: {}, End offset: {}, Total messages: {}",
                    topicName, beginningOffset, endOffset, totalMessages);

            return totalMessages;

        } catch (Exception e) {
            log.error("Error getting message count", e);
            throw new RuntimeException("Failed to get message count for topic: " + topicName, e);
        } finally {
            consumer.close();
        }
    }

    /**
     * Create a Kafka Consumer for browsing
     *
     * Key configurations:
     * - bootstrap.servers: Where to connect
     * - group.id: Consumer group name (separate from real consumers!)
     * - enable.auto.commit: false (we're just reading, not processing)
     * - auto.offset.reset: earliest (start from beginning if no offset)
     * - key/value deserializers: Convert bytes back to Strings
     *
     * Why a unique group ID?
     * - We don't want to interfere with actual message processing
     * - Our browsing shouldn't affect consumer offsets of real apps
     */
    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-manager-browser-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");  // We're just browsing, don't commit offsets
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");  // Start from beginning if no offset

        return new KafkaConsumer<>(props);
    }
}
