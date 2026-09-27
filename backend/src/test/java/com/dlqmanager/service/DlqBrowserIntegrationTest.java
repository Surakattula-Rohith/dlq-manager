package com.dlqmanager.service;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.dto.DlqMessageDto;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DlqBrowserIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DlqBrowserService dlqBrowserService;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @Test
    void pagesThroughEveryPartitionWithoutGapsOrDuplicates() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 3);
        produce(dlq, 0, 10, Map.of());
        produce(dlq, 1, 8, Map.of());
        produce(dlq, 2, 7, Map.of());
        UUID id = register(dlq);

        assertThat(dlqBrowserService.getMessageCount(id)).isEqualTo(25);

        Set<String> seen = new HashSet<>();
        for (int page = 1; page <= 3; page++) {
            for (DlqMessageDto message : dlqBrowserService.getMessages(id, page, 10)) {
                String position = message.getPartition() + ":" + message.getOffset();
                assertThat(seen.add(position)).as("message %s shown twice", position).isTrue();
            }
        }

        assertThat(seen).hasSize(25);
        assertThat(seen).anyMatch(position -> position.startsWith("2:"));
        assertThat(dlqBrowserService.getMessages(id, 4, 10)).isEmpty();
    }

    @Test
    void pagingStillWorksAfterRetentionRemovesOldMessages() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 2);
        produce(dlq, 0, 12, Map.of());
        produce(dlq, 1, 5, Map.of());
        // Simulate Kafka retention: partition 0 now starts at offset 8
        deleteRecordsBefore(dlq, 0, 8);
        UUID id = register(dlq);

        assertThat(dlqBrowserService.getMessageCount(id)).isEqualTo(9);
        assertThat(positions(dlqBrowserService.getMessages(id, 1, 5)))
                .containsExactly("0:8", "0:9", "0:10", "0:11", "1:0");
        assertThat(positions(dlqBrowserService.getMessages(id, 2, 5)))
                .containsExactly("1:1", "1:2", "1:3", "1:4");
    }

    @Test
    void errorBreakdownUnderstandsEveryHeaderStyle() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 1);
        produce(dlq, 0, 3, Map.of("X-Error-Message", "DB Connection Timeout"));
        produce(dlq, 0, 2, Map.of("kafka_dlt-exception-message", "Payment gateway timeout"));
        produce(dlq, 0, 1, Map.of("__connect.errors.exception.message", "Converter failure"));
        produce(dlq, 0, 1, Map.of());
        UUID id = register(dlq);

        assertThat(dlqBrowserService.getErrorBreakdown(id)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DB Connection Timeout", 3L,
                "Payment gateway timeout", 2L,
                "Converter failure", 1L,
                "Unknown Error", 1L
        ));
    }

    private UUID register(String dlqTopicName) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(dlqTopicName);
        topic.setSourceTopic(dlqTopicName.replace("-dlq", ""));
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.ACTIVE);
        return dlqTopicRepository.save(topic).getId();
    }

    private static List<String> positions(List<DlqMessageDto> messages) {
        return messages.stream().map(m -> m.getPartition() + ":" + m.getOffset()).toList();
    }

    private static void deleteRecordsBefore(String topic, int partition, long offset) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.deleteRecords(Map.of(new TopicPartition(topic, partition), RecordsToDelete.beforeOffset(offset)))
                    .all().get();
        }
    }
}
