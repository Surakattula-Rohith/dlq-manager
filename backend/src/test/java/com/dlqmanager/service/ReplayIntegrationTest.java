package com.dlqmanager.service;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.dto.BulkReplayRequestDto;
import com.dlqmanager.model.dto.ReplayJobDto;
import com.dlqmanager.model.dto.ReplayRequestDto;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.model.enums.ReplayStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import com.dlqmanager.repository.ReplayJobRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ReplayService replayService;

    @Autowired
    private DlqBrowserService dlqBrowserService;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @Autowired
    private ReplayJobRepository replayJobRepository;

    @Test
    void replaysToSourceTopicAndStripsDlqHeaders() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 1, Map.of(
                "X-Error-Message", "DB Connection Timeout",
                "kafka_dlt-exception-stacktrace", "java.sql.SQLException at ...",
                "traceparent", "00-abc-def-01"
        ));
        UUID id = register(dlq, source);

        ReplayJobDto job = replayService.bulkReplayMessages(bulk(id, false, 0L));

        assertThat(job.getSucceeded()).isEqualTo(1);
        List<ConsumerRecord<String, String>> replayed = readAll(source, 1);
        assertThat(replayed).hasSize(1);
        assertThat(replayed.get(0).value()).isEqualTo("{\"orderId\":\"ORD-0-0\"}");
        assertThat(headerNames(replayed.get(0)))
                .contains("traceparent", "X-Replayed-At", "X-Replayed-By")
                .doesNotContain("X-Error-Message", "kafka_dlt-exception-stacktrace");

        // History must be readable outside a web request (no open-session-in-view here)
        assertThat(replayService.getReplayHistoryForDlq(id))
                .extracting(ReplayJobDto::getDlqTopicName)
                .containsExactly(dlq);
        assertThat(replayService.getReplayJob(job.getId()).getSourceTopic()).isEqualTo(source);
    }

    @Test
    void blocksDuplicateReplayUnlessForced() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 2, Map.of());
        UUID id = register(dlq, source);

        ReplayJobDto first = replayService.bulkReplayMessages(bulk(id, false, 0L, 1L));
        ReplayJobDto second = replayService.bulkReplayMessages(bulk(id, false, 0L, 1L));

        assertThat(first.getSucceeded()).isEqualTo(2);
        assertThat(second.getSucceeded()).isZero();
        assertThat(second.getFailed()).isEqualTo(2);

        assertThatThrownBy(() -> replayService.replayMessage(single(id, 0L, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already replayed");

        ReplayJobDto forced = replayService.replayMessage(single(id, 0L, true));
        assertThat(forced.getStatus()).isEqualTo(ReplayStatus.COMPLETED);

        // 2 from the first run + 1 forced; the blocked attempts sent nothing
        assertThat(readAll(source, 3)).hasSize(3);

        DlqBrowserService.MessageCounts counts = dlqBrowserService.getMessageCounts(id);
        assertThat(counts.total()).isEqualTo(2);
        assertThat(counts.replayed()).isEqualTo(2);
        assertThat(counts.pending()).isZero();

        // "Hide replayed" filter leaves nothing to show
        assertThat(dlqBrowserService.searchMessages(id, new MessageFilter(null, null, true), 1, 10).matching())
                .isZero();
    }

    @Test
    void failedReplayIsKeptInHistory() throws Exception {
        String source = uniqueTopic("orders");
        String dlq = source + "-dlq";
        createTopic(source, 1);
        createTopic(dlq, 1);
        produce(dlq, 0, 1, Map.of());
        UUID id = register(dlq, source);

        // Offset 99 doesn't exist, so the replay fails
        assertThatThrownBy(() -> replayService.replayMessage(single(id, 99L, false)))
                .isInstanceOf(RuntimeException.class);

        assertThat(replayJobRepository.findByDlqTopicIdAndStatus(id, ReplayStatus.FAILED)).hasSize(1);
    }

    // --- Helpers ---

    private UUID register(String dlqTopicName, String sourceTopic) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(dlqTopicName);
        topic.setSourceTopic(sourceTopic);
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.ACTIVE);
        return dlqTopicRepository.save(topic).getId();
    }

    private static BulkReplayRequestDto bulk(UUID dlqTopicId, boolean force, Long... offsets) {
        BulkReplayRequestDto request = new BulkReplayRequestDto();
        request.setDlqTopicId(dlqTopicId);
        request.setInitiatedBy("it-test");
        request.setForce(force);
        request.setMessages(Arrays.stream(offsets)
                .map(offset -> new BulkReplayRequestDto.MessageIdentifier(offset, 0))
                .toList());
        return request;
    }

    private static ReplayRequestDto single(UUID dlqTopicId, long offset, boolean force) {
        ReplayRequestDto request = new ReplayRequestDto();
        request.setDlqTopicId(dlqTopicId);
        request.setMessageOffset(offset);
        request.setMessagePartition(0);
        request.setInitiatedBy("it-test");
        request.setForce(force);
        return request;
    }

    /**
     * Read everything from partition 0 of a topic, waiting up to 15 seconds for the expected number of messages
     */
    private static List<ConsumerRecord<String, String>> readAll(String topic, int expected) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-reader-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));

            long deadline = System.currentTimeMillis() + 15_000;
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
            // One more short poll, so extra (unexpected) messages would also show up
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        return records;
    }

    private static List<String> headerNames(ConsumerRecord<String, String> record) {
        List<String> names = new ArrayList<>();
        for (Header header : record.headers()) {
            names.add(header.key());
        }
        return names;
    }
}
