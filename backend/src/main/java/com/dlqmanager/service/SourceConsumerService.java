package com.dlqmanager.service;

import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.repository.DlqTopicRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.kafka.common.ConsumerGroupState;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Source consumers: who reads the topic a DLQ's messages are replayed to
 *
 * A replay puts messages back on the source topic (e.g. "orders"). Whether that helps
 * depends on the service reading it: if it isn't running, or is far behind, the replayed
 * messages just wait - or fail again. This shows each consumer group of the source topic
 * with its lag and a plain status.
 *
 * Looking up every consumer group is a handful of Kafka requests, so the answer is
 * remembered for 30 seconds per source topic (several people often have the same DLQ open).
 */
@Service
public class SourceConsumerService {

    private static final Duration REMEMBER_FOR = Duration.ofSeconds(30);

    public enum Status {
        /** Running and has processed everything */
        CAUGHT_UP,
        /** Running, but messages are waiting */
        BEHIND,
        /** No consumer of the group is running */
        NOT_RUNNING,
        /** Consumers are joining or leaving; Kafka is reassigning partitions */
        REBALANCING
    }

    /**
     * @param state   Kafka's group state, e.g. STABLE or EMPTY
     * @param members consumers currently in the group
     * @param lag     messages not processed yet
     */
    public record SourceConsumer(String groupId, String state, int members, long lag, Status status) {
    }

    /**
     * @param sourceTopic the DLQ's source topic
     * @param topicExists false if the source topic is not in Kafka (replays would fail)
     * @param consumers   groups reading the source topic, the ones needing attention first
     */
    public record SourceConsumers(String sourceTopic, boolean topicExists, List<SourceConsumer> consumers) {
    }

    private final DlqTopicRepository dlqTopicRepository;
    private final KafkaAdminService kafkaAdminService;
    private final KafkaConfigService kafkaConfigService;
    private final Cache<String, SourceConsumers> recent = Caffeine.newBuilder()
            .expireAfterWrite(REMEMBER_FOR)
            .maximumSize(1_000)
            .build();

    public SourceConsumerService(DlqTopicRepository dlqTopicRepository,
                                 KafkaAdminService kafkaAdminService,
                                 KafkaConfigService kafkaConfigService) {
        this.dlqTopicRepository = dlqTopicRepository;
        this.kafkaAdminService = kafkaAdminService;
        this.kafkaConfigService = kafkaConfigService;
    }

    /**
     * @throws IllegalArgumentException if the DLQ topic doesn't exist
     */
    public SourceConsumers getSourceConsumers(UUID dlqTopicId) {
        DlqTopic dlqTopic = dlqTopicRepository.findById(dlqTopicId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ topic not found: " + dlqTopicId));
        String sourceTopic = dlqTopic.getSourceTopic();
        if (sourceTopic == null || sourceTopic.isBlank()) {
            return new SourceConsumers(sourceTopic, false, List.of());
        }

        // Another Kafka cluster has other consumers, so the address is part of the key
        String key = kafkaConfigService.getBootstrapServers() + "|" + sourceTopic;
        return recent.get(key, ignored -> lookUp(sourceTopic));
    }

    private SourceConsumers lookUp(String sourceTopic) {
        return kafkaAdminService.getConsumerLag(sourceTopic)
                .map(groups -> new SourceConsumers(sourceTopic, true, groups.stream()
                        .map(group -> new SourceConsumer(group.groupId(), group.state().name(), group.members(),
                                group.lag(), status(group.state(), group.members(), group.lag())))
                        .sorted(Comparator.comparing((SourceConsumer consumer) -> attentionOrder(consumer.status()))
                                .thenComparing(SourceConsumer::lag, Comparator.reverseOrder())
                                .thenComparing(SourceConsumer::groupId))
                        .toList()))
                .orElseGet(() -> new SourceConsumers(sourceTopic, false, List.of()));
    }

    static Status status(ConsumerGroupState state, int members, long lag) {
        return switch (state) {
            case PREPARING_REBALANCE, COMPLETING_REBALANCE, ASSIGNING, RECONCILING -> Status.REBALANCING;
            case EMPTY, DEAD -> Status.NOT_RUNNING;
            // STABLE, or a state this client doesn't know: go by whether consumers are there
            default -> members == 0 ? Status.NOT_RUNNING : (lag == 0 ? Status.CAUGHT_UP : Status.BEHIND);
        };
    }

    /**
     * Groups that need someone's attention are listed first
     */
    private static int attentionOrder(Status status) {
        return switch (status) {
            case NOT_RUNNING -> 0;
            case BEHIND -> 1;
            case REBALANCING -> 2;
            case CAUGHT_UP -> 3;
        };
    }
}
