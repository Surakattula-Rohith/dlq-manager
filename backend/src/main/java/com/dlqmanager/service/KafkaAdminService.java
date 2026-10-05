package com.dlqmanager.service;

import com.dlqmanager.config.KafkaConnection;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Service for interacting with Kafka Admin API
 * Handles operations like listing topics, getting topic details, etc.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaAdminService {

    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    /**
     * How long a page waits for Kafka when reading consumer groups
     */
    private static final long LOOKUP_TIMEOUT_SECONDS = 10;

    private final KafkaConfigService kafkaConfigService;

    private AdminClient adminClient;
    private KafkaConnection adminClientConnection;

    /**
     * List all Kafka topics in the cluster
     *
     * @return List of topic names
     * @throws RuntimeException if connection to Kafka fails
     */
    public List<String> listAllTopics() {
        log.debug("Fetching all Kafka topics...");

        try {
            AdminClient adminClient = adminClient();
            // List all topics (including internal topics)
            Collection<TopicListing> topicListings = adminClient.listTopics(
                new ListTopicsOptions().listInternal(false)
            ).listings().get();

            List<String> topics = topicListings.stream()
                .map(TopicListing::name)
                .sorted()
                .collect(Collectors.toList());

            log.info("Found {} Kafka topics", topics.size());
            return topics;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to list Kafka topics", e);
            throw new RuntimeException("Failed to connect to Kafka: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a specific topic exists in Kafka
     *
     * @param topicName The name of the topic to check
     * @return true if topic exists, false otherwise
     */
    public boolean topicExists(String topicName) {
        log.debug("Checking if topic exists: {}", topicName);

        try {
            AdminClient adminClient = adminClient();
            Set<String> existingTopics = adminClient.listTopics().names().get();
            boolean exists = existingTopics.contains(topicName);

            log.debug("Topic '{}' exists: {}", topicName, exists);
            return exists;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to check if topic exists: {}", topicName, e);
            throw new RuntimeException("Failed to check topic existence: " + e.getMessage(), e);
        }
    }

    /**
     * Discover potential DLQ topics based on naming conventions
     * Looks for topics ending with: -dlq, -dead-letter, -error, .DLQ
     *
     * @return Map of DLQ topic name -> potential source topic name
     */
    public Map<String, String> discoverDlqTopics() {
        log.debug("Auto-discovering DLQ topics...");

        List<String> allTopics = listAllTopics();
        Map<String, String> dlqMappings = new HashMap<>();

        // Common DLQ suffixes
        String[] dlqSuffixes = {"-dlq", "-dead-letter", "-error", ".DLQ", "_dlq"};

        for (String topic : allTopics) {
            for (String suffix : dlqSuffixes) {
                if (topic.toLowerCase().endsWith(suffix.toLowerCase())) {
                    // Extract source topic by removing suffix
                    String sourceTopic = topic.substring(0, topic.length() - suffix.length());

                    // Verify source topic exists in Kafka
                    if (allTopics.contains(sourceTopic)) {
                        dlqMappings.put(topic, sourceTopic);
                        log.info("Discovered DLQ mapping: {} -> {}", topic, sourceTopic);
                    } else {
                        // Source doesn't exist, but still add with guessed name
                        dlqMappings.put(topic, sourceTopic);
                        log.warn("Discovered DLQ '{}' but source topic '{}' doesn't exist", topic, sourceTopic);
                    }
                    break; // Only match first suffix
                }
            }
        }

        log.info("Auto-discovered {} DLQ topics", dlqMappings.size());
        return dlqMappings;
    }

    /**
     * Get detailed information about Kafka cluster
     *
     * @return Map with cluster information (broker count, cluster ID, etc.)
     */
    public Map<String, Object> getClusterInfo() {
        log.debug("Fetching Kafka cluster information...");

        try {
            AdminClient adminClient = adminClient();
            Map<String, Object> clusterInfo = new HashMap<>();

            // Get cluster ID
            String clusterId = adminClient.describeCluster().clusterId().get();
            clusterInfo.put("clusterId", clusterId);

            // Get broker count
            int brokerCount = adminClient.describeCluster().nodes().get().size();
            clusterInfo.put("brokerCount", brokerCount);

            // Get topic count
            int topicCount = adminClient.listTopics().names().get().size();
            clusterInfo.put("topicCount", topicCount);

            log.info("Cluster Info - ID: {}, Brokers: {}, Topics: {}", clusterId, brokerCount, topicCount);
            return clusterInfo;

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get cluster information", e);
            throw new RuntimeException("Failed to get cluster info: " + e.getMessage(), e);
        }
    }

    /**
     * A consumer group that reads a topic (it has committed offsets there)
     *
     * @param groupId the group's name - usually the name of the service that consumes
     * @param state   Kafka's view of the group: STABLE (consumers running), EMPTY (none running), rebalancing, ...
     * @param members consumers currently in the group
     * @param lag     messages in the topic the group has not processed yet
     */
    public record ConsumerGroupLag(String groupId, ConsumerGroupState state, int members, long lag) {
    }

    /**
     * Every consumer group that reads the given topic, and how far behind each one is
     *
     * lag = latest offset - committed offset, added up over the topic's partitions
     *
     * Groups this app may not read (on a secured cluster with ACLs) are left out instead of
     * failing the whole lookup.
     *
     * @return empty if the topic doesn't exist
     */
    public Optional<List<ConsumerGroupLag>> getConsumerLag(String topicName) {
        try {
            AdminClient adminClient = adminClient();
            List<TopicPartition> partitions = partitionsOf(adminClient, topicName);
            if (partitions == null) {
                return Optional.empty();
            }

            Collection<ConsumerGroupListing> groups = adminClient.listConsumerGroups().all()
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (groups.isEmpty()) {
                return Optional.of(List.of());
            }

            // Committed offsets of every group, for this topic's partitions only, in one request
            Map<String, ListConsumerGroupOffsetsSpec> specs = new HashMap<>();
            for (ConsumerGroupListing group : groups) {
                specs.put(group.groupId(), new ListConsumerGroupOffsetsSpec().topicPartitions(partitions));
            }
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(specs);

            Map<String, Map<TopicPartition, OffsetAndMetadata>> readers = new HashMap<>();
            for (String groupId : specs.keySet()) {
                try {
                    Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>();
                    offsetsResult.partitionsToOffsetAndMetadata(groupId).get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .forEach((partition, offset) -> {
                                if (offset != null) {
                                    committed.put(partition, offset);
                                }
                            });
                    if (!committed.isEmpty()) {
                        readers.put(groupId, committed);
                    }
                } catch (ExecutionException e) {
                    log.debug("Skipping consumer group {}: {}", groupId, e.getMessage());
                }
            }
            if (readers.isEmpty()) {
                return Optional.of(List.of());
            }

            Map<TopicPartition, OffsetSpec> latest = partitions.stream()
                    .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = adminClient.listOffsets(latest).all()
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            var descriptions = adminClient.describeConsumerGroups(readers.keySet()).describedGroups();

            List<ConsumerGroupLag> result = new ArrayList<>();
            for (Map.Entry<String, Map<TopicPartition, OffsetAndMetadata>> reader : readers.entrySet()) {
                long lag = 0;
                for (Map.Entry<TopicPartition, OffsetAndMetadata> committed : reader.getValue().entrySet()) {
                    ListOffsetsResult.ListOffsetsResultInfo end = endOffsets.get(committed.getKey());
                    if (end != null) {
                        lag += Math.max(0, end.offset() - committed.getValue().offset());
                    }
                }

                ConsumerGroupState state = ConsumerGroupState.UNKNOWN;
                int members = 0;
                try {
                    ConsumerGroupDescription description = descriptions.get(reader.getKey())
                            .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    state = description.state();
                    members = description.members().size();
                } catch (ExecutionException e) {
                    log.debug("Could not describe consumer group {}: {}", reader.getKey(), e.getMessage());
                }
                result.add(new ConsumerGroupLag(reader.getKey(), state, members, lag));
            }
            return Optional.of(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while reading consumer groups", e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to read consumer groups of topic {}", topicName, e);
            throw new RuntimeException("Failed to read consumer groups: " + e.getMessage(), e);
        }
    }

    /**
     * @return the topic's partitions, or null if the topic doesn't exist
     */
    private static List<TopicPartition> partitionsOf(AdminClient adminClient, String topicName)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            TopicDescription description = adminClient.describeTopics(List.of(topicName)).allTopicNames()
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).get(topicName);
            return description.partitions().stream()
                    .map(partition -> new TopicPartition(topicName, partition.partition()))
                    .toList();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                return null;
            }
            throw e;
        }
    }

    /**
     * One shared admin client (it is thread-safe), rebuilt only when the Kafka connection
     * changes in Settings. Creating one per call meant a new connection for every topic
     * check and every refresh of the cluster info.
     */
    private synchronized AdminClient adminClient() {
        KafkaConnection connection = kafkaConfigService.getConnection();
        if (adminClient == null || !connection.equals(adminClientConnection)) {
            if (adminClient != null) {
                log.info("Kafka connection changed from {} to {}, recreating admin client",
                        adminClientConnection, connection);
                adminClient.close(CLOSE_TIMEOUT);
            }
            adminClient = AdminClient.create(connection.clientProperties());
            adminClientConnection = connection;
        }
        return adminClient;
    }

    @PreDestroy
    public synchronized void close() {
        if (adminClient != null) {
            adminClient.close(CLOSE_TIMEOUT);
            adminClient = null;
        }
    }
}
