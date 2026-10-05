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

import java.time.Instant;
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

    @Test
    void errorBreakdownIsRememberedWhileCountsStayLive() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 1);
        produce(dlq, 0, 3, Map.of("X-Error-Message", "DB Connection Timeout"));
        UUID id = register(dlq);
        assertThat(dlqBrowserService.getErrorBreakdown(id)).containsEntry("DB Connection Timeout", 3L);

        produce(dlq, 0, 2, Map.of("X-Error-Message", "DB Connection Timeout"));

        // Scanned a moment ago, so the breakdown comes from memory...
        assertThat(dlqBrowserService.getErrorBreakdown(id)).containsEntry("DB Connection Timeout", 3L);
        // ...while the count is read from Kafka every time
        assertThat(dlqBrowserService.getMessageCount(id)).isEqualTo(5);
    }

    @Test
    void searchFindsMatchesAcrossPartitionsAndPages() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 3);
        produce(dlq, 0, 6, Map.of("X-Error-Message", "DB Connection Timeout"));
        produce(dlq, 1, 4, Map.of("X-Error-Message", "Validation Failed"));
        produce(dlq, 2, 5, Map.of("X-Error-Message", "DB Connection Timeout"));
        UUID id = register(dlq);

        // Error type filter: 11 matches spread over partitions 0 and 2 -> 2 pages of 10
        MessageFilter byError = new MessageFilter(null, "DB Connection Timeout", false);
        DlqBrowserService.SearchResult page1 = dlqBrowserService.searchMessages(id, byError, 1, 10);
        DlqBrowserService.SearchResult page2 = dlqBrowserService.searchMessages(id, byError, 2, 10);

        assertThat(page1.matching()).isEqualTo(11);
        assertThat(page1.messages()).hasSize(10)
                .allMatch(m -> "DB Connection Timeout".equals(m.getErrorMessage()));
        assertThat(page2.messages()).hasSize(1);
        assertThat(page1.scanLimitReached()).isFalse();

        // Text search (keys look like ORD-<partition>-<n>), case-insensitive
        DlqBrowserService.SearchResult byKey = dlqBrowserService.searchMessages(
                id, new MessageFilter("ord-1-", null, false), 1, 10);

        assertThat(byKey.matching()).isEqualTo(4);
        assertThat(byKey.messages()).allMatch(m -> m.getPartition() == 1);
    }

    @Test
    void timeWindowFindsExactlyTheMessagesThatLandedInIt() throws Exception {
        String dlq = uniqueTopic("orders-dlq");
        createTopic(dlq, 2);
        Instant noon = Instant.parse("2026-10-05T12:00:00Z");
        // Partition 0, written in time order
        produceAt(dlq, 0, "early", noon.minusSeconds(3 * 3600), Map.of("X-Error-Message", "DB Connection Timeout"));
        produceAt(dlq, 0, "in-window-1", noon.plusSeconds(60), Map.of("X-Error-Message", "DB Connection Timeout"));
        produceAt(dlq, 0, "in-window-2", noon.plusSeconds(1800), Map.of("X-Error-Message", "Validation Failed"));
        produceAt(dlq, 0, "late", noon.plusSeconds(2 * 3600), Map.of("X-Error-Message", "DB Connection Timeout"));
        // A publisher that keeps the original record's time: written last, but it belongs in the window
        produceAt(dlq, 0, "in-window-out-of-order", noon.plusSeconds(600), Map.of("X-Error-Message", "DB Connection Timeout"));
        // Partition 1 has nothing that recent
        produceAt(dlq, 1, "yesterday", noon.minusSeconds(86_400), Map.of());
        UUID id = register(dlq);

        MessageFilter window = new MessageFilter(null, null, false, noon, noon.plusSeconds(3600));
        DlqBrowserService.SearchResult inWindow = dlqBrowserService.searchMessages(id, window, 1, 10);

        assertThat(inWindow.matching()).isEqualTo(3);
        assertThat(inWindow.messages()).extracting(DlqMessageDto::getMessageKey)
                .containsExactly("in-window-1", "in-window-2", "in-window-out-of-order");

        // Combined with an error type
        MessageFilter timeouts = new MessageFilter(null, "DB Connection Timeout", false, noon, noon.plusSeconds(3600));
        assertThat(dlqBrowserService.searchMessages(id, timeouts, 1, 10).messages())
                .extracting(DlqMessageDto::getMessageKey)
                .containsExactly("in-window-1", "in-window-out-of-order");

        // Open-ended: everything from noon on
        MessageFilter sinceNoon = new MessageFilter(null, null, false, noon, null);
        assertThat(dlqBrowserService.searchMessages(id, sinceNoon, 1, 10).matching()).isEqualTo(4);

        // A window after the last message
        MessageFilter future = new MessageFilter(null, null, false, noon.plusSeconds(86_400), null);
        assertThat(dlqBrowserService.searchMessages(id, future, 1, 10).matching()).isZero();
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
