package com.dlqmanager.config;

import com.dlqmanager.service.KafkaConfigService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Properties;

/**
 * Configuration class for Kafka Producer
 *
 * Purpose: Set up KafkaProducer bean for message replay
 *
 * How it works:
 * 1. Spring reads @Configuration classes at startup
 * 2. Methods with @Bean annotation create objects (beans)
 * 3. Spring stores these beans in its container
 * 4. Other classes can inject these beans (Dependency Injection)
 *
 * Example: ReplayProducer will receive this KafkaProducer automatically
 */
@Configuration
public class KafkaProducerConfig {

    private final KafkaConfigService kafkaConfigService;

    public KafkaProducerConfig(@Lazy KafkaConfigService kafkaConfigService) {
        this.kafkaConfigService = kafkaConfigService;
    }

    /**
     * Creates a KafkaProducer bean for sending messages
     *
     * @Bean annotation: Tells Spring to manage this object
     * Spring will call this method once at startup and store the result
     *
     * KafkaProducer<String, String> means:
     * - Key type: String (e.g., "ORD-12345")
     * - Value type: String (JSON payload)
     *
     * @return configured KafkaProducer instance
     */
    @Bean
    public KafkaProducer<String, String> kafkaProducer() {
        Properties props = new Properties();

        /*
         * BOOTSTRAP_SERVERS_CONFIG: Where to find Kafka brokers
         * Example: "localhost:9092" or "broker1:9092,broker2:9092"
         * Producer will connect to these addresses
         */
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigService.getBootstrapServers());

        /*
         * KEY_SERIALIZER_CLASS_CONFIG: How to convert key to bytes
         * StringSerializer: Converts String → byte[] using UTF-8
         * Kafka stores everything as bytes, so we need serializers
         */
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        /*
         * VALUE_SERIALIZER_CLASS_CONFIG: How to convert value to bytes
         * StringSerializer: Converts JSON string → byte[]
         * Alternative: Could use JsonSerializer for automatic conversion
         */
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        /*
         * ACKS_CONFIG: Acknowledgment level for reliability
         * Options:
         * - "0": No acknowledgment (fire and forget, fastest but risky)
         * - "1": Leader acknowledges (default, balanced)
         * - "all": All replicas acknowledge (slowest but most reliable)
         *
         * For replay, we use "all" to ensure messages are safely stored
         * We don't want to "successfully replay" a message that gets lost!
         */
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        /*
         * RETRIES_CONFIG: How many times to retry on failure
         * Default: 0 (don't retry)
         * We set 3: If send fails, try 3 more times automatically
         * Handles transient network issues
         */
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        /*
         * REQUEST_TIMEOUT_MS_CONFIG: How long to wait for response
         * Default: 30000 (30 seconds)
         * If Kafka doesn't respond in 30s, consider it failed
         * Prevents hanging forever on network issues
         */
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        /*
         * ENABLE_IDEMPOTENCE_CONFIG: Prevent duplicate messages
         * true: Even if we retry, Kafka guarantees exactly-once delivery
         * false: Retry might create duplicates (same message stored twice)
         *
         * For replay, idempotence is important:
         * - Network timeout might happen after Kafka received message
         * - We retry, but Kafka already has it
         * - With idempotence: Kafka detects duplicate and ignores it
         * - Without: Same message replayed twice
         */
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        /*
         * COMPRESSION_TYPE_CONFIG: Compress messages before sending
         * Options: "none", "gzip", "snappy", "lz4", "zstd"
         * "snappy": Good balance of speed and compression ratio
         * Reduces network bandwidth, especially for large JSON payloads
         */
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        /*
         * CLIENT_ID_CONFIG: Identifier for this producer in logs
         * Helps with debugging and monitoring
         * Shows up in Kafka broker logs
         */
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "dlq-manager-replay-producer");

        // Create and return the KafkaProducer
        return new KafkaProducer<>(props);
    }

    /*
     * Why not use Spring Kafka's KafkaTemplate?
     *
     * Spring Kafka provides KafkaTemplate which is a wrapper around KafkaProducer.
     * It's more "Spring-like" and easier to use.
     *
     * We use raw KafkaProducer because:
     * 1. More control over configuration
     * 2. Direct access to producer callbacks
     * 3. Simpler for understanding Kafka concepts
     * 4. Matches existing DlqBrowserService pattern (uses raw KafkaConsumer)
     *
     * For production, KafkaTemplate might be better:
     * - Automatic Spring transaction management
     * - Better error handling
     * - Simpler API
     *
     * Future enhancement: Migrate to KafkaTemplate
     */
}
