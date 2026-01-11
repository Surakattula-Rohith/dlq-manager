package com.dlqmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for producing (sending) messages to Kafka topics
 *
 * Purpose: Wrap KafkaProducer with replay-specific logic
 * - Send messages to source topics
 * - Handle errors gracefully
 * - Add replay marker headers
 * - Synchronous send with timeout
 *
 * Why a separate service?
 * - Encapsulates Kafka producer complexity
 * - Reusable across different replay scenarios
 * - Easy to mock for testing
 * - Single responsibility: just send messages
 */
@Service
@Slf4j
public class ReplayProducer {

    private final KafkaProducer<String, String> kafkaProducer;

    /**
     * Constructor injection
     * Spring automatically injects the kafkaProducer bean from KafkaProducerConfig
     *
     * @param kafkaProducer configured KafkaProducer instance
     */
    public ReplayProducer(KafkaProducer<String, String> kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Send a message to a Kafka topic (synchronous)
     *
     * Flow:
     * 1. Create ProducerRecord with topic, key, value, headers
     * 2. Send to Kafka (async by default)
     * 3. Wait for acknowledgment (block until success or timeout)
     * 4. Return metadata (partition, offset) on success
     * 5. Throw exception on failure
     *
     * @param topic         destination topic name (e.g., "orders")
     * @param key           message key (e.g., "ORD-12345"), can be null
     * @param value         message payload (JSON string)
     * @param originalHeaders headers from DLQ message
     * @return RecordMetadata containing partition and offset where message was stored
     * @throws Exception if sending fails (timeout, broker error, serialization error)
     */
    public RecordMetadata sendMessage(String topic, String key, String value, List<Header> originalHeaders)
            throws Exception {

        log.info("Sending message to topic: {}, key: {}", topic, key);

        // Step 1: Prepare headers
        List<Header> headers = prepareHeaders(originalHeaders);

        // Step 2: Create ProducerRecord
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,      // Topic name
                null,       // Partition (null = let Kafka choose based on key)
                key,        // Message key
                value,      // Message payload
                headers     // Headers
        );

        try {
            // Step 3: Send message (returns Future immediately)
            Future<RecordMetadata> future = kafkaProducer.send(record);

            // Step 4: Block and wait for result (synchronous)
            // Timeout: 30 seconds (matches producer config)
            RecordMetadata metadata = future.get(30, TimeUnit.SECONDS);

            // Step 5: Log success
            log.info("Message sent successfully to topic: {}, partition: {}, offset: {}",
                    metadata.topic(), metadata.partition(), metadata.offset());

            return metadata;

        } catch (InterruptedException e) {
            // Thread was interrupted while waiting
            log.error("Message send interrupted for topic: {}, key: {}", topic, key, e);
            Thread.currentThread().interrupt(); // Restore interrupt status
            throw new Exception("Message send was interrupted", e);

        } catch (ExecutionException e) {
            // Kafka producer threw an exception (broker error, serialization, etc.)
            log.error("Failed to send message to topic: {}, key: {}", topic, key, e.getCause());
            throw new Exception("Failed to send message: " + e.getCause().getMessage(), e.getCause());

        } catch (TimeoutException e) {
            // Didn't receive acknowledgment within 30 seconds
            log.error("Timeout sending message to topic: {}, key: {}", topic, key, e);
            throw new Exception("Kafka send timeout after 30 seconds", e);
        }
    }

    /**
     * Prepare headers for replay
     *
     * Logic:
     * 1. Copy original headers (preserve business metadata)
     * 2. Remove DLQ-specific headers (we don't want these in source topic)
     * 3. Add replay marker header (X-Replayed-At with timestamp)
     *
     * Headers to REMOVE (DLQ-specific):
     * - X-Error-Message: Error info, not relevant in source topic
     * - X-Retry-Count: Retry attempts, fresh start in source topic
     * - X-Exception-Class: Java exception, not relevant
     * - X-Failed-Timestamp: When it failed, not relevant
     * - X-Consumer-Group: Original consumer, might be different now
     *
     * Headers to KEEP:
     * - X-Original-Topic: Useful for tracking message origin
     * - Business headers: correlation-id, trace-id, etc.
     *
     * Headers to ADD:
     * - X-Replayed-At: Timestamp when replayed (ISO 8601 format)
     * - X-Replayed-By: Who initiated replay (future enhancement)
     *
     * @param originalHeaders headers from DLQ message
     * @return cleaned headers with replay marker
     */
    private List<Header> prepareHeaders(List<Header> originalHeaders) {
        List<Header> cleanedHeaders = new ArrayList<>();

        // List of header keys to remove
        List<String> headersToRemove = List.of(
                "X-Error-Message",
                "X-Retry-Count",
                "X-Exception-Class",
                "X-Failed-Timestamp",
                "X-Consumer-Group"
        );

        // Copy original headers except the ones we want to remove
        if (originalHeaders != null) {
            for (Header header : originalHeaders) {
                String headerKey = header.key();
                if (!headersToRemove.contains(headerKey)) {
                    cleanedHeaders.add(header);
                    log.debug("Keeping header: {}", headerKey);
                } else {
                    log.debug("Removing DLQ header: {}", headerKey);
                }
            }
        }

        // Add replay marker header
        String replayTimestamp = Instant.now().toString(); // ISO 8601 format: 2026-01-11T10:30:00Z
        Header replayHeader = new RecordHeader(
                "X-Replayed-At",
                replayTimestamp.getBytes(StandardCharsets.UTF_8)
        );
        cleanedHeaders.add(replayHeader);
        log.debug("Added replay marker header: X-Replayed-At={}", replayTimestamp);

        return cleanedHeaders;
    }

    /**
     * Flush and close the producer (for graceful shutdown)
     * Should be called when application stops
     *
     * Flush: Wait for all pending sends to complete
     * Close: Release resources (network connections, threads)
     */
    public void close() {
        log.info("Closing Kafka producer...");
        if (kafkaProducer != null) {
            kafkaProducer.flush(); // Wait for pending sends
            kafkaProducer.close(); // Clean up resources
        }
        log.info("Kafka producer closed successfully");
    }
}
