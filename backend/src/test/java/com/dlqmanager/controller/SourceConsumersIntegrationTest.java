package com.dlqmanager.controller;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consumer groups of a DLQ's source topic, read from a real Kafka
 */
@AutoConfigureMockMvc
class SourceConsumersIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @Test
    void stoppedConsumerShowsAsNotRunningWithWhatItHasLeft() throws Exception {
        String source = uniqueTopic("orders");
        createTopic(source, 1);
        produce(source, 0, 10, Map.of());
        // The service processed 6 of the 10 messages and then stopped
        commitWithoutRunning("billing-" + source, source, 6);

        JsonNode result = sourceConsumers(register(source));

        assertThat(result.get("sourceTopic").asText()).isEqualTo(source);
        assertThat(result.get("topicExists").asBoolean()).isTrue();
        JsonNode billing = result.get("consumers").get(0);
        assertThat(billing.get("groupId").asText()).isEqualTo("billing-" + source);
        assertThat(billing.get("status").asText()).isEqualTo("NOT_RUNNING");
        assertThat(billing.get("state").asText()).isEqualTo("EMPTY");
        assertThat(billing.get("members").asInt()).isZero();
        assertThat(billing.get("lag").asLong()).isEqualTo(4);
    }

    @Test
    void runningConsumersShowWhetherTheyAreCaughtUpOrBehind() throws Exception {
        String source = uniqueTopic("orders");
        createTopic(source, 2);
        produce(source, 0, 5, Map.of());
        produce(source, 1, 5, Map.of());

        try (KafkaConsumer<String, String> caughtUp = runningConsumer("audit-" + source, source);
             KafkaConsumer<String, String> behind = runningConsumer("search-" + source, source)) {
            commit(caughtUp, source, Map.of(0, 5L, 1, 5L));
            commit(behind, source, Map.of(0, 2L, 1, 5L));

            JsonNode consumers = sourceConsumers(register(source)).get("consumers");

            // The one that needs attention is listed first
            assertThat(consumers).hasSize(2);
            assertThat(consumers.get(0).get("groupId").asText()).isEqualTo("search-" + source);
            assertThat(consumers.get(0).get("status").asText()).isEqualTo("BEHIND");
            assertThat(consumers.get(0).get("lag").asLong()).isEqualTo(3);
            assertThat(consumers.get(0).get("members").asInt()).isEqualTo(1);
            assertThat(consumers.get(1).get("groupId").asText()).isEqualTo("audit-" + source);
            assertThat(consumers.get(1).get("status").asText()).isEqualTo("CAUGHT_UP");
        }
    }

    @Test
    void sourceTopicWithoutConsumersOrMissingFromKafka() throws Exception {
        String unread = uniqueTopic("orders");
        createTopic(unread, 1);

        JsonNode noReaders = sourceConsumers(register(unread));
        JsonNode missing = sourceConsumers(register(uniqueTopic("never-created")));

        assertThat(noReaders.get("topicExists").asBoolean()).isTrue();
        assertThat(noReaders.get("consumers")).isEmpty();
        assertThat(missing.get("topicExists").asBoolean()).isFalse();
    }

    @Test
    void unknownDlqIsNotFound() throws Exception {
        mockMvc.perform(get("/api/dlq-topics/" + UUID.randomUUID() + "/source-consumers").with(httpBasic("viewer", "viewer")))
                .andExpect(status().isNotFound());
    }

    private JsonNode sourceConsumers(UUID dlqTopicId) throws Exception {
        String json = mockMvc.perform(get("/api/dlq-topics/" + dlqTopicId + "/source-consumers")
                        .with(httpBasic("viewer", "viewer")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    /**
     * Offsets committed by a group that has no consumer running (Kafka calls it EMPTY)
     */
    private static void commitWithoutRunning(String groupId, String topic, long offset) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId))) {
            consumer.commitSync(Map.of(new TopicPartition(topic, 0), new OffsetAndMetadata(offset)));
        }
    }

    /**
     * A consumer that has joined its group and been given the topic's partitions (Kafka calls the group STABLE)
     */
    private static KafkaConsumer<String, String> runningConsumer(String groupId, String topic) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(groupId));
        consumer.subscribe(List.of(topic));
        long deadline = System.currentTimeMillis() + 30_000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(200));
        }
        assertThat(consumer.assignment()).as("joined the group").isNotEmpty();
        return consumer;
    }

    private static void commit(KafkaConsumer<String, String> consumer, String topic, Map<Integer, Long> offsets) {
        Map<TopicPartition, OffsetAndMetadata> commits = new HashMap<>();
        offsets.forEach((partition, offset) -> commits.put(new TopicPartition(topic, partition), new OffsetAndMetadata(offset)));
        consumer.commitSync(commits);
    }

    private static Map<String, Object> consumerProps(String groupId) {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    }

    /**
     * Registered as PAUSED so the scheduler running in this shared context leaves it alone
     */
    private UUID register(String sourceTopic) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(sourceTopic + "-dlq");
        topic.setSourceTopic(sourceTopic);
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.PAUSED);
        return dlqTopicRepository.save(topic).getId();
    }
}
