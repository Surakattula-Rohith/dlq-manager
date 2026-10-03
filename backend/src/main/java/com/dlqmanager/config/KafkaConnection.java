package com.dlqmanager.config;

import org.apache.kafka.clients.CommonClientConfigs;

import java.util.HashMap;
import java.util.Map;

/**
 * How to reach the Kafka cluster
 *
 * Every Kafka client the app creates starts from clientProperties():
 * - the consumers that browse DLQ messages (KafkaConsumerPool)
 * - the producer that replays them (KafkaProducerConfig)
 * - the admin client that lists topics (KafkaAdminService)
 * - the connection test on the Settings page (KafkaConfigService)
 *
 * So a connection setting only has to be added here to apply everywhere.
 *
 * It is a record: two values are equal when they describe the same connection. The
 * long-lived clients remember the value they were built from and rebuild themselves
 * when the one saved in Settings is no longer equal to it.
 *
 * @param bootstrapServers Kafka brokers, e.g. "localhost:9092" or "broker1:9092,broker2:9092"
 */
public record KafkaConnection(String bootstrapServers) {

    public KafkaConnection {
        bootstrapServers = bootstrapServers.trim();
    }

    /**
     * Connection properties shared by consumers, producers and admin clients.
     * A new map every time, so the caller can add its own client settings to it.
     */
    public Map<String, Object> clientProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return props;
    }
}
