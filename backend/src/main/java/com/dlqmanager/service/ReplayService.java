package com.dlqmanager.service;

import com.dlqmanager.model.dto.BulkReplayRequestDto;
import com.dlqmanager.model.dto.ReplayJobDto;
import com.dlqmanager.model.dto.ReplayRequestDto;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.entity.ReplayJob;
import com.dlqmanager.model.entity.ReplayMessage;
import com.dlqmanager.model.enums.ActivityAction;
import com.dlqmanager.model.enums.ReplayMessageStatus;
import com.dlqmanager.model.enums.ReplayStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import com.dlqmanager.repository.ReplayJobRepository;
import com.dlqmanager.repository.ReplayMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
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
 * - Prevent replaying the same message twice by accident
 *
 * Flow for single message replay:
 * 1. Look up DLQ topic in database
 * 2. Claim the message (only one replay can hold it at a time), then check it
 *    wasn't already replayed (unless force=true)
 * 3. Create ReplayJob record (status: PENDING)
 * 4. Read message from DLQ using Kafka consumer
 * 5. Send message to source topic using ReplayProducer
 * 6. Update ReplayJob status (COMPLETED/FAILED)
 * 7. Create ReplayMessage record with result
 * 8. Return ReplayJobDto to caller
 *
 * Why no @Transactional on the replay methods?
 * - The replay records are our audit trail
 * - If the whole method ran in one transaction, a failed replay would roll back
 *   the FAILED job record too, and failures would never show up in Replay History
 * - Each save() commits on its own, so success AND failure are always recorded
 *
 * Note: we keep working with our own ReplayJob instance and ignore what save() returns.
 * For an existing entity save() returns a merged copy whose DlqTopic is a lazy proxy,
 * and reading it after the save's transaction has closed throws LazyInitializationException
 * (it only worked inside web requests because of Spring's open-session-in-view).
 */
@Service
@Slf4j
public class ReplayService {

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_POLL_ATTEMPTS = 3;

    private final DlqTopicRepository dlqTopicRepository;
    private final ReplayJobRepository replayJobRepository;
    private final ReplayMessageRepository replayMessageRepository;
    private final ReplayProducer replayProducer;
    private final KafkaConsumerPool consumerPool;
    private final ActivityLogService activityLogService;
    private final ReplayClaimService replayClaimService;
    private final KafkaAdminService kafkaAdminService;

    public ReplayService(
            DlqTopicRepository dlqTopicRepository,
            ReplayJobRepository replayJobRepository,
            ReplayMessageRepository replayMessageRepository,
            ReplayProducer replayProducer,
            KafkaConsumerPool consumerPool,
            ActivityLogService activityLogService,
            ReplayClaimService replayClaimService,
            KafkaAdminService kafkaAdminService
    ) {
        this.dlqTopicRepository = dlqTopicRepository;
        this.replayJobRepository = replayJobRepository;
        this.replayMessageRepository = replayMessageRepository;
        this.replayProducer = replayProducer;
        this.consumerPool = consumerPool;
        this.activityLogService = activityLogService;
        this.replayClaimService = replayClaimService;
        this.kafkaAdminService = kafkaAdminService;
    }

    /**
     * Where a replay sends its messages
     *
     * @param topic the source topic, or the topic chosen for a test replay
     * @param test  true for a test replay: the messages stay pending, because the real
     *              replay to the source topic hasn't happened
     */
    private record Destination(String topic, boolean test) {
        String describe() {
            return test ? "test replay to " + topic + ": " : "";
        }
    }

    /**
     * @param targetTopic empty (or the source topic itself) for a normal replay
     * @throws IllegalArgumentException if the test topic is the DLQ itself or doesn't exist
     */
    private Destination destinationOf(DlqTopic dlqTopic, String targetTopic) {
        if (targetTopic == null || targetTopic.isBlank() || targetTopic.trim().equals(dlqTopic.getSourceTopic())) {
            return new Destination(dlqTopic.getSourceTopic(), false);
        }
        String topic = targetTopic.trim();
        if (topic.equals(dlqTopic.getDlqTopicName())) {
            throw new IllegalArgumentException("Messages can't be replayed into the DLQ they came from");
        }
        if (!kafkaAdminService.topicExists(topic)) {
            throw new IllegalArgumentException("Topic '" + topic + "' doesn't exist in Kafka");
        }
        return new Destination(topic, true);
    }

    /**
     * Replay a single message from DLQ to source topic
     *
     * Steps:
     * 1. Validate: DLQ topic exists in database
     * 2. Validate: message not already replayed (unless force=true)
     * 3. Create: ReplayJob record (audit trail)
     * 4. Read: Message from DLQ at specified offset/partition
     * 5. Send: Message to source topic
     * 6. Record: Success/failure in database
     * 7. Return: ReplayJobDto with results
     *
     * @param request contains dlqTopicId, messageOffset, messagePartition
     * @return ReplayJobDto with job details and status
     * @throws RuntimeException if DLQ not found, message already replayed, or replay fails
     */
    public ReplayJobDto replayMessage(ReplayRequestDto request) {
        log.info("Starting single message replay for DLQ topic ID: {}, offset: {}, partition: {}",
                request.getDlqTopicId(), request.getMessageOffset(), request.getMessagePartition());

        // Step 1: Look up DLQ topic
        DlqTopic dlqTopic = dlqTopicRepository.findById(request.getDlqTopicId())
                .orElseThrow(() -> new RuntimeException("DLQ topic not found: " + request.getDlqTopicId()));

        log.info("Found DLQ topic: {} → source topic: {}", dlqTopic.getDlqTopicName(), dlqTopic.getSourceTopic());

        String initiatedBy = request.getInitiatedBy() != null ? request.getInitiatedBy() : "system";
        int partition = request.getMessagePartition();
        long offset = request.getMessageOffset();
        Destination destination = destinationOf(dlqTopic, request.getTargetTopic());

        // Step 2: Claim the message, so nobody else can replay it at the same moment
        UUID claimId = replayClaimService.tryClaim(dlqTopic.getId(), partition, offset, initiatedBy)
                .orElseThrow(() -> new IllegalStateException(beingReplayedMessage(partition, offset)));
        try {
            return replayClaimedMessage(request, dlqTopic, initiatedBy, destination);
        } finally {
            replayClaimService.release(claimId);
        }
    }

    /**
     * The rest of a single replay. Only runs while holding the claim on the message,
     * so the "already replayed?" check can't race with another replay of it.
     */
    private ReplayJobDto replayClaimedMessage(ReplayRequestDto request, DlqTopic dlqTopic, String initiatedBy,
                                              Destination destination) {
        // Don't send the same message twice unless the caller explicitly asks for it.
        // A test replay goes somewhere else, so it may repeat (and may follow a real replay).
        if (!destination.test() && !Boolean.TRUE.equals(request.getForce())) {
            List<ReplayMessage> previous = replayMessageRepository.findReplaysOfMessage(
                    dlqTopic.getId(), request.getMessagePartition(), request.getMessageOffset(), ReplayMessageStatus.SUCCESS);
            if (!previous.isEmpty()) {
                throw new IllegalStateException(alreadyReplayedMessage(
                        request.getMessagePartition(), request.getMessageOffset(), previous.get(0).getReplayedAt()));
            }
        }

        // Step 3: Create ReplayJob record
        ReplayJob replayJob = new ReplayJob();
        replayJob.setDlqTopic(dlqTopic);
        replayJob.setInitiatedBy(initiatedBy);
        replayJob.setStatus(ReplayStatus.PENDING);
        replayJob.setTotalMessages(1);  // Single message
        replayJob.setTargetTopic(destination.test() ? destination.topic() : null);
        replayJob.setSucceeded(0);
        replayJob.setFailed(0);
        replayJobRepository.save(replayJob);
        log.info("Created replay job with ID: {}", replayJob.getId());

        try (KafkaConsumerPool.Lease lease = consumerPool.borrow()) {
            KafkaConsumer<String, String> consumer = lease.consumer();
            // Step 4: Update status to RUNNING
            replayJob.setStatus(ReplayStatus.RUNNING);
            replayJob.setStartedAt(LocalDateTime.now());
            replayJobRepository.save(replayJob);

            // Step 5: Read message from DLQ
            log.info("Reading message from DLQ topic: {}, offset: {}, partition: {}",
                    dlqTopic.getDlqTopicName(), request.getMessageOffset(), request.getMessagePartition());

            ConsumerRecord<String, String> record = readMessageFromDlq(
                    consumer,
                    dlqTopic.getDlqTopicName(),
                    request.getMessagePartition(),
                    request.getMessageOffset()
            );

            if (record == null) {
                throw new RuntimeException("Message not found at offset: " + request.getMessageOffset());
            }

            log.info("Successfully read message. Key: {}, Value length: {} bytes",
                    record.key(), record.value() != null ? record.value().length() : 0);

            // Step 6: Send message to the source topic (or the test topic)
            log.info("Sending message to topic: {}", destination.topic());

            List<Header> headers = new ArrayList<>();
            record.headers().forEach(headers::add);

            RecordMetadata metadata = replayProducer.sendMessage(
                    destination.topic(),
                    record.key(),
                    record.value(),
                    headers,
                    initiatedBy,
                    destination.test()
            );

            log.info("Message sent successfully. Partition: {}, Offset: {}",
                    metadata.partition(), metadata.offset());

            // Step 7: Update job status - SUCCESS
            replayJob.setSucceeded(1);
            replayJob.setStatus(ReplayStatus.COMPLETED);
            replayJob.setCompletedAt(LocalDateTime.now());
            replayJobRepository.save(replayJob);

            // Step 8: Create ReplayMessage record - SUCCESS
            createReplayMessageRecord(
                    replayJob,
                    record.key(),
                    record.offset(),
                    record.partition(),
                    ReplayMessageStatus.SUCCESS,
                    null
            );

            log.info("Replay job completed successfully: {}", replayJob.getId());
            activityLogService.record(initiatedBy, ActivityAction.MESSAGES_REPLAYED, dlqTopic.getDlqTopicName(),
                    destination.describe() + describeSingle(request, "sent"));

        } catch (Exception e) {
            log.error("Replay failed for job: {}", replayJob.getId(), e);

            // Update job status - FAILED (committed on its own, so it stays in the audit trail)
            replayJob.setFailed(1);
            replayJob.setStatus(ReplayStatus.FAILED);
            replayJob.setCompletedAt(LocalDateTime.now());
            replayJobRepository.save(replayJob);

            // Create ReplayMessage record - FAILED
            createReplayMessageRecord(
                    replayJob,
                    null,  // We might not know the key if read failed
                    request.getMessageOffset(),
                    request.getMessagePartition(),
                    ReplayMessageStatus.FAILED,
                    e.getMessage()
            );

            activityLogService.record(initiatedBy, ActivityAction.MESSAGES_REPLAYED, dlqTopic.getDlqTopicName(),
                    destination.describe() + describeSingle(request, "failed: " + e.getMessage()));

            throw new RuntimeException("Failed to replay message: " + e.getMessage(), e);
        }

        // Step 9: Convert to DTO and return
        return ReplayJobDto.fromEntity(replayJob);
    }

    /**
     * Replay multiple messages from DLQ to source topic (Bulk Replay)
     *
     * Flow:
     * 1. Validate: DLQ topic exists
     * 2. Create: ReplayJob for N messages
     * 3. Loop: For each message (one Kafka consumer is reused for all of them)
     *    a. Claim it - skip if someone else is replaying it right now, or if it was
     *       already replayed (unless force=true); skipped messages are recorded as FAILED with the reason
     *    b. Read from DLQ
     *    c. Send to source topic
     *    d. Record success/failure
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
    public ReplayJobDto bulkReplayMessages(BulkReplayRequestDto request) {
        log.info("Starting bulk replay for DLQ topic ID: {}, message count: {}",
                request.getDlqTopicId(), request.getMessages().size());

        // Step 1: Look up DLQ topic
        DlqTopic dlqTopic = dlqTopicRepository.findById(request.getDlqTopicId())
                .orElseThrow(() -> new RuntimeException("DLQ topic not found: " + request.getDlqTopicId()));

        log.info("Found DLQ topic: {} → source topic: {}", dlqTopic.getDlqTopicName(), dlqTopic.getSourceTopic());

        String initiatedBy = request.getInitiatedBy() != null ? request.getInitiatedBy() : "system";
        Destination destination = destinationOf(dlqTopic, request.getTargetTopic());
        // A test replay goes somewhere else, so "already replayed" doesn't block it
        boolean force = Boolean.TRUE.equals(request.getForce()) || destination.test();

        // Step 2: Create ReplayJob record
        ReplayJob replayJob = new ReplayJob();
        replayJob.setDlqTopic(dlqTopic);
        replayJob.setInitiatedBy(initiatedBy);
        replayJob.setStatus(ReplayStatus.PENDING);
        replayJob.setTotalMessages(request.getMessages().size());
        replayJob.setTargetTopic(destination.test() ? destination.topic() : null);
        replayJob.setSucceeded(0);
        replayJob.setFailed(0);
        replayJobRepository.save(replayJob);
        log.info("Created bulk replay job with ID: {}", replayJob.getId());

        // Step 3: Update status to RUNNING
        replayJob.setStatus(ReplayStatus.RUNNING);
        replayJob.setStartedAt(LocalDateTime.now());
        replayJobRepository.save(replayJob);

        int successCount = 0;
        int failureCount = 0;
        Set<String> listed = new HashSet<>();

        // Step 4: Process each message
        try (KafkaConsumerPool.Lease lease = consumerPool.borrow()) {
            KafkaConsumer<String, String> consumer = lease.consumer();
            for (BulkReplayRequestDto.MessageIdentifier msgId : request.getMessages()) {
                log.info("Processing message: offset={}, partition={}", msgId.getOffset(), msgId.getPartition());

                // Same message listed twice in one request? Only send it once.
                if (!listed.add(ReplayMessageRepository.offsetKey(msgId.getPartition(), msgId.getOffset()))) {
                    failureCount++;
                    createReplayMessageRecord(replayJob, null, msgId.getOffset(), msgId.getPartition(),
                            ReplayMessageStatus.FAILED, "Listed more than once in this replay");
                    continue;
                }

                // Someone else replaying this message right now? Skip it rather than send it twice.
                Optional<UUID> claimId = replayClaimService.tryClaim(
                        dlqTopic.getId(), msgId.getPartition(), msgId.getOffset(), initiatedBy);
                if (claimId.isEmpty()) {
                    log.warn("Skipping message being replayed elsewhere: offset={}, partition={}",
                            msgId.getOffset(), msgId.getPartition());
                    failureCount++;
                    createReplayMessageRecord(replayJob, null, msgId.getOffset(), msgId.getPartition(),
                            ReplayMessageStatus.FAILED, beingReplayedMessage(msgId.getPartition(), msgId.getOffset()));
                    continue;
                }

                try {
                    if (replayClaimedBulkMessage(consumer, replayJob, dlqTopic, msgId, force, initiatedBy, destination)) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } finally {
                    replayClaimService.release(claimId.get());
                }
            }
        }

        // Step 5: Update job with final counts
        replayJob.setSucceeded(successCount);
        replayJob.setFailed(failureCount);
        replayJob.setStatus(ReplayStatus.COMPLETED);
        replayJob.setCompletedAt(LocalDateTime.now());
        replayJobRepository.save(replayJob);

        log.info("Bulk replay completed. Job ID: {}, Succeeded: {}, Failed: {}",
                replayJob.getId(), successCount, failureCount);
        activityLogService.record(initiatedBy, ActivityAction.MESSAGES_REPLAYED, dlqTopic.getDlqTopicName(),
                destination.describe() + String.format("%d message(s): %d succeeded, %d failed%s",
                        request.getMessages().size(), successCount, failureCount,
                        Boolean.TRUE.equals(request.getForce()) ? " (forced)" : ""));

        // Step 6: Convert to DTO and return
        return ReplayJobDto.fromEntity(replayJob);
    }

    /**
     * Replay one message of a bulk replay and record the result.
     * Only runs while holding the claim on the message.
     *
     * @return true if the message was sent to the destination
     */
    private boolean replayClaimedBulkMessage(KafkaConsumer<String, String> consumer, ReplayJob replayJob,
                                             DlqTopic dlqTopic, BulkReplayRequestDto.MessageIdentifier msgId,
                                             boolean force, String initiatedBy, Destination destination) {
        // Already replayed? Record why it was skipped instead of sending a duplicate
        if (!force) {
            List<ReplayMessage> previous = replayMessageRepository.findReplaysOfMessage(
                    dlqTopic.getId(), msgId.getPartition(), msgId.getOffset(), ReplayMessageStatus.SUCCESS);
            if (!previous.isEmpty()) {
                log.warn("Skipping already replayed message: offset={}, partition={}", msgId.getOffset(), msgId.getPartition());
                createReplayMessageRecord(replayJob, null, msgId.getOffset(), msgId.getPartition(),
                        ReplayMessageStatus.FAILED,
                        alreadyReplayedMessage(msgId.getPartition(), msgId.getOffset(), previous.get(0).getReplayedAt()));
                return false;
            }
        }

        try {
            // Read message from DLQ
            ConsumerRecord<String, String> record = readMessageFromDlq(
                    consumer,
                    dlqTopic.getDlqTopicName(),
                    msgId.getPartition(),
                    msgId.getOffset()
            );

            if (record == null) {
                log.warn("Message not found at offset: {}, partition: {}", msgId.getOffset(), msgId.getPartition());
                createReplayMessageRecord(replayJob, null, msgId.getOffset(), msgId.getPartition(),
                        ReplayMessageStatus.FAILED, "Message not found at offset: " + msgId.getOffset());
                return false;
            }

            // Send message to the source topic (or the test topic)
            List<Header> headers = new ArrayList<>();
            record.headers().forEach(headers::add);

            RecordMetadata metadata = replayProducer.sendMessage(
                    destination.topic(),
                    record.key(),
                    record.value(),
                    headers,
                    initiatedBy,
                    destination.test()
            );

            log.info("Message replayed successfully. Key: {}, Offset: {}", record.key(), metadata.offset());
            createReplayMessageRecord(replayJob, record.key(), record.offset(), record.partition(),
                    ReplayMessageStatus.SUCCESS, null);
            return true;

        } catch (Exception e) {
            log.error("Failed to replay message at offset: {}, partition: {}", msgId.getOffset(), msgId.getPartition(), e);
            createReplayMessageRecord(replayJob, null, msgId.getOffset(), msgId.getPartition(),
                    ReplayMessageStatus.FAILED, e.getMessage());
            return false;
        }
    }

    /**
     * Activity log text for a single replay, e.g. "partition 0, offset 42: sent"
     */
    private static String describeSingle(ReplayRequestDto request, String outcome) {
        String forced = Boolean.TRUE.equals(request.getForce()) ? " (forced)" : "";
        return "partition " + request.getMessagePartition() + ", offset " + request.getMessageOffset()
                + forced + ": " + outcome;
    }

    /**
     * Helper method to create ReplayMessage record
     * Extracted to avoid code duplication
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

    private static String beingReplayedMessage(Integer partition, Long offset) {
        return String.format("Message at partition %d, offset %d is being replayed by someone else right now. "
                + "Check Replay History in a moment.", partition, offset);
    }

    private String alreadyReplayedMessage(Integer partition, Long offset, LocalDateTime replayedAt) {
        return String.format("Message at partition %d, offset %d was already replayed%s. Send force=true to replay it again.",
                partition, offset, replayedAt != null ? " at " + replayedAt : "");
    }

    /**
     * Read a specific message from DLQ topic
     *
     * How it works:
     * 1. Assign the consumer to the specific partition
     * 2. Seek to exact offset
     * 3. Poll until we see that offset (or pass it)
     *
     * @param consumer consumer to use (reused across a bulk replay)
     * @param topicName topic to read from
     * @param partition partition number
     * @param offset exact offset to read
     * @return ConsumerRecord at that position, or null if not found
     */
    private ConsumerRecord<String, String> readMessageFromDlq(KafkaConsumer<String, String> consumer,
                                                              String topicName, int partition, long offset) {
        TopicPartition topicPartition = new TopicPartition(topicName, partition);
        consumer.assign(Collections.singletonList(topicPartition));

        // Seek to the exact offset
        consumer.seek(topicPartition, offset);

        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);

            for (ConsumerRecord<String, String> record : records.records(topicPartition)) {
                if (record.offset() == offset) {
                    return record;
                }
                if (record.offset() > offset) {
                    // The offset no longer exists (e.g. removed by retention)
                    return null;
                }
            }
        }

        // Message not found
        return null;
    }

    /**
     * Get replay job by ID
     *
     * @param jobId UUID of the replay job
     * @return ReplayJobDto with job details
     * @throws RuntimeException if job not found
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<ReplayJobDto> getReplayHistoryForDlq(UUID dlqTopicId) {
        log.info("Fetching replay history for DLQ topic: {}", dlqTopicId);

        List<ReplayJob> jobs = replayJobRepository.findByDlqTopicId(dlqTopicId);

        return jobs.stream()
                .map(ReplayJobDto::fromEntity)
                .toList();
    }
}
