package com.dlqmanager.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Builds KafkaProducers for message replay
 *
 * Why not a single @Bean producer?
 * - Kafka bootstrap servers can be changed from the Settings page at runtime
 * - A @Bean is created once at startup, so it would keep sending to the OLD cluster
 * - ReplayProducer asks this class for a new producer whenever the servers change
 */
@Component
public class KafkaProducerConfig {

    /**
     * Creates a KafkaProducer for sending messages to the given cluster
     *
     * KafkaProducer<String, String> means:
     * - Key type: String (e.g., "ORD-12345")
     * - Value type: String (JSON payload)
     *
     * @param bootstrapServers Kafka brokers, e.g. "localhost:9092" or "broker1:9092,broker2:9092"
     * @return configured KafkaProducer instance
     */
    public KafkaProducer<String, String> createProducer(String bootstrapServers) {
        Properties props = new Properties();

        /*
         * BOOTSTRAP_SERVERS_CONFIG: Where to find Kafka brokers
         * Producer will connect to these addresses
         */
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        /*
         * KEY/VALUE_SERIALIZER_CLASS_CONFIG: How to convert key and value to bytes
         * StringSerializer: Converts String → byte[] using UTF-8
         */
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        /*
         * ACKS_CONFIG: "all" - every in-sync replica must confirm the write
         * We don't want to "successfully replay" a message that gets lost!
         */
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        /*
         * RETRIES_CONFIG: retry transient network errors automatically
         */
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        /*
         * REQUEST_TIMEOUT_MS_CONFIG: consider a request failed after 30 seconds
         */
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        /*
         * ENABLE_IDEMPOTENCE_CONFIG: the producer's own retries won't write duplicates
         *
         * Note: this only protects against retries inside one send.
         * It does NOT stop the same DLQ message being replayed twice by a user.
         */
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        /*
         * COMPRESSION_TYPE_CONFIG: "snappy" - good balance of speed and size
         */
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        /*
         * CLIENT_ID_CONFIG: shows up in broker logs, helps with debugging
         */
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "dlq-manager-replay-producer");

        return new KafkaProducer<>(props);
    }
}
