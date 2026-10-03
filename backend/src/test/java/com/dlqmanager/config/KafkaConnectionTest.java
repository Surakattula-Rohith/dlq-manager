package com.dlqmanager.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConnectionTest {

    @Test
    void clientPropertiesPointAtTheCluster() {
        KafkaConnection connection = new KafkaConnection("broker1:9092,broker2:9092");

        assertThat(connection.clientProperties())
                .containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
    }

    @Test
    void sameAddressIsTheSameConnectionSoClientsAreNotRebuilt() {
        assertThat(new KafkaConnection(" kafka:9092 ")).isEqualTo(new KafkaConnection("kafka:9092"));
        assertThat(new KafkaConnection("kafka-a:9092")).isNotEqualTo(new KafkaConnection("kafka-b:9092"));
    }

    @Test
    void everyClientGetsItsOwnCopyOfTheProperties() {
        KafkaConnection connection = new KafkaConnection("kafka:9092");

        Map<String, Object> consumerProps = connection.clientProperties();
        consumerProps.put("group.id", "browser");

        assertThat(connection.clientProperties()).doesNotContainKey("group.id");
    }
}
