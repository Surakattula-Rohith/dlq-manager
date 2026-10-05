package com.dlqmanager.service;

import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.repository.DlqTopicRepository;
import com.dlqmanager.service.KafkaAdminService.ConsumerGroupLag;
import com.dlqmanager.service.SourceConsumerService.SourceConsumer;
import com.dlqmanager.service.SourceConsumerService.SourceConsumers;
import com.dlqmanager.service.SourceConsumerService.Status;
import org.apache.kafka.common.ConsumerGroupState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceConsumerServiceTest {

    private final DlqTopicRepository topics = mock(DlqTopicRepository.class);
    private final KafkaAdminService kafka = mock(KafkaAdminService.class);
    private final KafkaConfigService kafkaConfig = mock(KafkaConfigService.class);
    private final SourceConsumerService service = new SourceConsumerService(topics, kafka, kafkaConfig);
    private final UUID dlqId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        DlqTopic dlq = new DlqTopic();
        dlq.setId(dlqId);
        dlq.setDlqTopicName("orders-dlq");
        dlq.setSourceTopic("orders");
        when(topics.findById(dlqId)).thenReturn(Optional.of(dlq));
        when(kafkaConfig.getBootstrapServers()).thenReturn("kafka-a:9092");
    }

    @Test
    void statusSaysWhetherTheConsumerIsKeepingUp() {
        assertThat(SourceConsumerService.status(ConsumerGroupState.STABLE, 2, 0)).isEqualTo(Status.CAUGHT_UP);
        assertThat(SourceConsumerService.status(ConsumerGroupState.STABLE, 2, 1_234)).isEqualTo(Status.BEHIND);
        assertThat(SourceConsumerService.status(ConsumerGroupState.EMPTY, 0, 0)).isEqualTo(Status.NOT_RUNNING);
        assertThat(SourceConsumerService.status(ConsumerGroupState.EMPTY, 0, 50)).isEqualTo(Status.NOT_RUNNING);
        assertThat(SourceConsumerService.status(ConsumerGroupState.PREPARING_REBALANCE, 1, 10)).isEqualTo(Status.REBALANCING);
        // A state this version doesn't know: decided by whether consumers are there
        assertThat(SourceConsumerService.status(ConsumerGroupState.UNKNOWN, 0, 3)).isEqualTo(Status.NOT_RUNNING);
        assertThat(SourceConsumerService.status(ConsumerGroupState.UNKNOWN, 1, 0)).isEqualTo(Status.CAUGHT_UP);
    }

    @Test
    void groupsThatNeedAttentionComeFirst() {
        when(kafka.getConsumerLag("orders")).thenReturn(Optional.of(List.of(
                new ConsumerGroupLag("analytics", ConsumerGroupState.STABLE, 1, 0),
                new ConsumerGroupLag("billing", ConsumerGroupState.STABLE, 3, 40),
                new ConsumerGroupLag("order-processor", ConsumerGroupState.EMPTY, 0, 1_200),
                new ConsumerGroupLag("search-indexer", ConsumerGroupState.STABLE, 1, 900))));

        SourceConsumers result = service.getSourceConsumers(dlqId);

        assertThat(result.sourceTopic()).isEqualTo("orders");
        assertThat(result.topicExists()).isTrue();
        assertThat(result.consumers()).extracting(SourceConsumer::groupId)
                .containsExactly("order-processor", "search-indexer", "billing", "analytics");
        assertThat(result.consumers().get(0).status()).isEqualTo(Status.NOT_RUNNING);
        assertThat(result.consumers().get(0).state()).isEqualTo("EMPTY");
    }

    @Test
    void missingSourceTopicIsReported() {
        when(kafka.getConsumerLag("orders")).thenReturn(Optional.empty());

        SourceConsumers result = service.getSourceConsumers(dlqId);

        assertThat(result.topicExists()).isFalse();
        assertThat(result.consumers()).isEmpty();
    }

    @Test
    void theAnswerIsRememberedBrieflyPerKafkaCluster() {
        when(kafka.getConsumerLag("orders")).thenReturn(Optional.of(List.of()));

        service.getSourceConsumers(dlqId);
        service.getSourceConsumers(dlqId);
        verify(kafka, times(1)).getConsumerLag("orders");

        // Another cluster has other consumers
        when(kafkaConfig.getBootstrapServers()).thenReturn("kafka-b:9092");
        service.getSourceConsumers(dlqId);
        verify(kafka, times(2)).getConsumerLag("orders");
    }

    @Test
    void unknownDlqTopicIsReported() {
        UUID unknown = UUID.randomUUID();
        when(topics.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSourceConsumers(unknown)).isInstanceOf(IllegalArgumentException.class);
    }
}
