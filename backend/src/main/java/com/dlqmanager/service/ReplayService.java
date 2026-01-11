package com.dlqmanager.service;

import com.dlqmanager.model.dto.BulkReplayRequestDto;
import com.dlqmanager.model.dto.ReplayJobDto;
import com.dlqmanager.model.dto.ReplayRequestDto;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.entity.ReplayJob;
import com.dlqmanager.model.entity.ReplayMessage;
import com.dlqmanager.model.enums.ReplayMessageStatus;
import com.dlqmanager.model.enums.ReplayStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import com.dlqmanager.repository.ReplayJobRepository;
import com.dlqmanager.repository.ReplayMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for replaying messages from DLQ to source topics
 *
 * Purpose: Orchestrate the replay process
 * - Read message from DLQ
 * - Send to source topic
 * - Track status in database
 * - Handle errors
 *
 * Flow for single message replay:
 * 1. Look up DLQ topic in database
 * 2. Create ReplayJob record (status: PENDING)
 * 3. Read message from DLQ using Kafka consumer
 * 4. Send message to source topic using ReplayProducer
 * 5. Update ReplayJob status (COMPLETED/FAILED)
 * 6. Create ReplayMessage record with result
 * 7. Return ReplayJobDto to caller
 */
@Service
@Slf4j
public class ReplayService {

    private final DlqTopicRepository dlqTopicRepository;
    private final ReplayJobRepository replayJobRepository;
    private final ReplayMessageRepository replayMessageRepository;
    private final ReplayProducer replayProducer;
    private final String bootstrapServers;

    public ReplayService(
            DlqTopicRepository dlqTopicRepository,
            ReplayJobRepository replayJobRepository,
            ReplayMessageRepository replayMessageRepository,
            ReplayProducer replayProducer,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        this.dlqTopicRepository = dlqTopicRepository;
        this.replayJobRepository = replayJobRepository;
        this.replayMessageRepository = replayMessageRepository;
        this.replayProducer = replayProducer;
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * Replay a single message from DLQ to source topic
     *
     * Steps:
     * 1. Validate: DLQ topic exists in database
     * 2. Create: ReplayJob record (audit trail)
     * 3. Read: Message from DLQ at specified offset/partition
     * 4. Send: Message to source topic
     * 5. Record: Success/failure in database
     * 6. Return: ReplayJobDto with results
     *
     * @param request contains dlqTopicId, messageOffset, messagePartition
     * @return ReplayJobDto with job details and status
     * @throws RuntimeException if DLQ not found or replay fails
     */
    @Transactional
    public ReplayJobDto replayMessage(ReplayRequestDto request) {
        log.info("Starting single message replay for DLQ topic ID: {}, offset: {}, partition: {}",
                request.getDlqTopicId(), request.getMessageOffset(), request.getMessagePartition());

        // Step 1: Look up DLQ topic
        DlqTopic dlqTopic = dlqTopicRepository.findById(request.getDlqTopicId())
                .orElseThrow(() -> new RuntimeException("DLQ topic not found: " + request.getDlqTopicId()));

        log.info("Found DLQ topic: {} → source topic: {}", dlqTopic.getDlqTopicName(), dlqTopic.getSourceTopic());

        // Step 2: Create ReplayJob record
        ReplayJob replayJob = new ReplayJob();
        replayJob.setDlqTopic(dlqTopic);
        replayJob.setInitiatedBy(request.getInitiatedBy() != null ? request.getInitiatedBy() : "system");
        replayJob.setStatus(ReplayStatus.PENDING);
        replayJob.setTotalMessages(1);  // Single message
        replayJob.setSucceeded(0);
        replayJob.setFailed(0);
        replayJob = replayJobRepository.save(replayJob);
        log.info("Created replay job with ID: {}", replayJob.getId());

        try {
            // Step 3: Update status to RUNNING
            replayJob.setStatus(ReplayStatus.RUNNING);
            replayJob.setStartedAt(LocalDateTime.now());
            replayJob = replayJobRepository.save(replayJob);

            // Step 4: Read message from DLQ
            log.info("Reading message from DLQ topic: {}, offset: {}, partition: {}",
                    dlqTopic.getDlqTopicName(), request.getMessageOffset(), request.getMessagePartition());

            ConsumerRecord<String, String> record = readMessageFromDlq(
                    dlqTopic.getDlqTopicName(),
                    request.getMessagePartition(),
                    request.getMessageOffset()
            );

            if (record == null) {
                throw new RuntimeException("Message not found at offset: " + request.getMessageOffset());
            }

            log.info("Successfully read message. Key: {}, Value length: {} bytes",
                    record.key(), record.value() != null ? record.value().length() : 0);

            // Step 5: Send message to source topic
            log.info("Sending message to source topic: {}", dlqTopic.getSourceTopic());

            List<Header> headers = new ArrayList<>();
            record.headers().forEach(headers::add);

            RecordMetadata metadata = replayProducer.sendMessage(
                    dlqTopic.getSourceTopic(),
                    record.key(),
                    record.value(),
                    headers
            );

            log.info("Message sent successfully. Partition: {}, Offset: {}",
                    metadata.partition(), metadata.offset());

            // Step 6: Update job status - SUCCESS
            replayJob.setSucceeded(1);
            replayJob.setStatus(ReplayStatus.COMPLETED);
            replayJob.setCompletedAt(LocalDateTime.now());
            replayJob = replayJobRepository.save(replayJob);

            // Step 7: Create ReplayMessage record - SUCCESS
            ReplayMessage replayMessage = new ReplayMessage();
            replayMessage.setReplayJob(replayJob);
            replayMessage.setMessageKey(record.key());
            replayMessage.setDlqOffset(record.offset());
            replayMessage.setDlqPartition(record.partition());
            replayMessage.setStatus(ReplayMessageStatus.SUCCESS);
            replayMessage.setReplayedAt(LocalDateTime.now());
            replayMessageRepository.save(replayMessage);

            log.info("Replay job completed successfully: {}", replayJob.getId());

        } catch (Exception e) {
            log.error("Replay failed for job: {}", replayJob.getId(), e);

            // Update job status - FAILED
            replayJob.setFailed(1);
            replayJob.setStatus(ReplayStatus.FAILED);
            replayJob.setCompletedAt(LocalDateTime.now());
            replayJob = replayJobRepository.save(replayJob);

            // Create ReplayMessage record - FAILED
            ReplayMessage replayMessage = new ReplayMessage();
            replayMessage.setReplayJob(replayJob);
            replayMessage.setMessageKey(null);  // We might not know the key if read failed
            replayMessage.setDlqOffset(request.getMessageOffset());
            replayMessage.setDlqPartition(request.getMessagePartition());
            replayMessage.setStatus(ReplayMessageStatus.FAILED);
            replayMessage.setErrorMessage(e.getMessage());
            replayMessage.setReplayedAt(LocalDateTime.now());
            replayMessageRepository.save(replayMessage);

            throw new RuntimeException("Failed to replay message: " + e.getMessage(), e);
        }

        // Step 8: Convert to DTO and return
        return ReplayJobDto.fromEntity(replayJob);
    }

    /**
     * Replay multiple messages from DLQ to source topic (Bulk Replay)
     *
     * Flow:
     * 1. Validate: DLQ topic exists
     * 2. Create: ReplayJob for N messages
     * 3. Loop: For each message
     *    a. Read from DLQ
     *    b. Send to source topic
     *    c. Record success/failure
     * 4. Update: Final job status
     * 5. Return: ReplayJobDto
     *
     * Key Difference from Single Replay:
     * - Single replay: 1 job, 1 message, 1 ReplayMessage record
     * - Bulk replay: 1 job, N messages, N ReplayMessage records
     * - Job succeeds even if some messages fail (partial success)
     *
     * @param request contains dlqTopicId and list of messages (offset, partition)
     * @return ReplayJobDto with overall results
     * @throws RuntimeException if DLQ not found
     */
    @Transactional
    public ReplayJobDto bulkReplayMessages(BulkReplayRequestDto request) {
        log.info("Starting bulk replay for DLQ topic ID: {}, message count: {}",
                request.getDlqTopicId(), request.getMessages().size());

        // Step 1: Look up DLQ topic
        DlqTopic dlqTopic = dlqTopicRepository.findById(request.getDlqTopicId())
                .orElseThrow(() -> new RuntimeException("DLQ topic not found: " + request.getDlqTopicId()));

        log.info("Found DLQ topic: {} → source topic: {}", dlqTopic.getDlqTopicName(), dlqTopic.getSourceTopic());

        // Step 2: Create ReplayJob record
        ReplayJob replayJob = new ReplayJob();
        replayJob.setDlqTopic(dlqTopic);
        replayJob.setInitiatedBy(request.getInitiatedBy() != null ? request.getInitiatedBy() : "system");
        replayJob.setStatus(ReplayStatus.PENDING);
        replayJob.setTotalMessages(request.getMessages().size());
        replayJob.setSucceeded(0);
        replayJob.setFailed(0);
        replayJob = replayJobRepository.save(replayJob);
        log.info("Created bulk replay job with ID: {}", replayJob.getId());

        // Step 3: Update status to RUNNING
        replayJob.setStatus(ReplayStatus.RUNNING);
        replayJob.setStartedAt(LocalDateTime.now());
        replayJob = replayJobRepository.save(replayJob);

        int successCount = 0;
        int failureCount = 0;

        // Step 4: Process each message
        for (BulkReplayRequestDto.MessageIdentifier msgId : request.getMessages()) {
            log.info("Processing message: offset={}, partition={}", msgId.getOffset(), msgId.getPartition());

            try {
                // Read message from DLQ
                ConsumerRecord<String, String> record = readMessageFromDlq(
                        dlqTopic.getDlqTopicName(),
                        msgId.getPartition(),
                        msgId.getOffset()
                );

                if (record == null) {
                    log.warn("Message not found at offset: {}, partition: {}", msgId.getOffset(), msgId.getPartition());
                    failureCount++;

                    // Record failure
                    createReplayMessageRecord(
                            replayJob,
                            null,
                            msgId.getOffset(),
                            msgId.getPartition(),
                            ReplayMessageStatus.FAILED,
                            "Message not found at offset: " + msgId.getOffset()
                    );
                    continue;
                }

                // Send message to source topic
                List<Header> headers = new ArrayList<>();
                record.headers().forEach(headers::add);

                RecordMetadata metadata = replayProducer.sendMessage(
                        dlqTopic.getSourceTopic(),
                        record.key(),
                        record.value(),
                        headers
                );

                log.info("Message replayed successfully. Key: {}, Offset: {}", record.key(), metadata.offset());
                successCount++;

                // Record success
                createReplayMessageRecord(
                        replayJob,
                        record.key(),
                        record.offset(),
                        record.partition(),
                        ReplayMessageStatus.SUCCESS,
                        null
                );

            } catch (Exception e) {
                log.error("Failed to replay message at offset: {}, partition: {}",
                        msgId.getOffset(), msgId.getPartition(), e);
                failureCount++;

                // Record failure
                createReplayMessageRecord(
                        replayJob,
                        null,
                        msgId.getOffset(),
                        msgId.getPartition(),
                        ReplayMessageStatus.FAILED,
                        e.getMessage()
                );
            }
        }

        // Step 5: Update job with final counts
        replayJob.setSucceeded(successCount);
        replayJob.setFailed(failureCount);
        replayJob.setStatus(ReplayStatus.COMPLETED);
        replayJob.setCompletedAt(LocalDateTime.now());
        replayJob = replayJobRepository.save(replayJob);

        log.info("Bulk replay completed. Job ID: {}, Succeeded: {}, Failed: {}",
                replayJob.getId(), successCount, failureCount);

        // Step 6: Convert to DTO and return
        return ReplayJobDto.fromEntity(replayJob);
    }

    /**
     * Helper method to create ReplayMessage record
     * Extracted to avoid code duplication in bulk replay
     *
     * @param replayJob parent replay job
     * @param messageKey Kafka message key (can be null)
     * @param offset message offset in DLQ
     * @param partition message partition in DLQ
     * @param status SUCCESS or FAILED
     * @param errorMessage error message if failed, null if success
     */
    private void createReplayMessageRecord(
            ReplayJob replayJob,
            String messageKey,
            Long offset,
            Integer partition,
            ReplayMessageStatus status,
            String errorMessage) {

        ReplayMessage replayMessage = new ReplayMessage();
        replayMessage.setReplayJob(replayJob);
        replayMessage.setMessageKey(messageKey);
        replayMessage.setDlqOffset(offset);
        replayMessage.setDlqPartition(partition);
        replayMessage.setStatus(status);
        replayMessage.setErrorMessage(errorMessage);
        replayMessage.setReplayedAt(LocalDateTime.now());
        replayMessageRepository.save(replayMessage);
    }

    /**
     * Read a specific message from DLQ topic
     *
     * How it works:
     * 1. Create Kafka consumer
     * 2. Assign to specific partition
     * 3. Seek to exact offset
     * 4. Poll once to get the message
     * 5. Close consumer
     *
     * @param topicName topic to read from
     * @param partition partition number
     * @param offset exact offset to read
     * @return ConsumerRecord at that position, or null if not found
     */
    private ConsumerRecord<String, String> readMessageFromDlq(String topicName, int partition, long offset) {
        KafkaConsumer<String, String> consumer = createConsumer();

        try {
            // Assign to specific partition
            TopicPartition topicPartition = new TopicPartition(topicName, partition);
            consumer.assign(Collections.singletonList(topicPartition));

            // Seek to the exact offset
            consumer.seek(topicPartition, offset);

            // Poll to get the message
            // We only need one message, so poll once with short timeout
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            // Find the record at the exact offset
            for (ConsumerRecord<String, String> record : records) {
                if (record.offset() == offset && record.partition() == partition) {
                    return record;
                }
            }

            // Message not found
            return null;

        } finally {
            consumer.close();
        }
    }

    /**
     * Create a Kafka consumer for reading DLQ messages
     * Similar to DlqBrowserService but scoped to ReplayService
     *
     * @return configured KafkaConsumer
     */
    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-manager-replay-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new KafkaConsumer<>(props);
    }

    /**
     * Get replay job by ID
     *
     * @param jobId UUID of the replay job
     * @return ReplayJobDto with job details
     * @throws RuntimeException if job not found
     */
    public ReplayJobDto getReplayJob(UUID jobId) {
        log.info("Fetching replay job: {}", jobId);

        ReplayJob replayJob = replayJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));

        return ReplayJobDto.fromEntity(replayJob);
    }

    /**
     * Get replay history (all jobs, newest first)
     *
     * @return List of ReplayJobDto
     */
    public List<ReplayJobDto> getReplayHistory() {
        log.info("Fetching replay history");

        List<ReplayJob> jobs = replayJobRepository.findAllByOrderByCreatedAtDesc();

        return jobs.stream()
                .map(ReplayJobDto::fromEntity)
                .toList();
    }

    /**
     * Get replay history for a specific DLQ topic
     *
     * @param dlqTopicId UUID of the DLQ topic
     * @return List of ReplayJobDto for that DLQ
     */
    public List<ReplayJobDto> getReplayHistoryForDlq(UUID dlqTopicId) {
        log.info("Fetching replay history for DLQ topic: {}", dlqTopicId);

        List<ReplayJob> jobs = replayJobRepository.findByDlqTopicId(dlqTopicId);

        return jobs.stream()
                .map(ReplayJobDto::fromEntity)
                .toList();
    }
}
