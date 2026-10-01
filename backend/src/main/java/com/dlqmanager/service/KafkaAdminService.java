package com.dlqmanager.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.TopicListing;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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

    private final KafkaConfigService kafkaConfigService;

    private AdminClient adminClient;
    private String adminClientBootstrapServers;

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
     * One shared admin client (it is thread-safe), rebuilt only when the Kafka address
     * changes in Settings. Creating one per call meant a new connection for every topic
     * check and every refresh of the cluster info.
     */
    private synchronized AdminClient adminClient() {
        String bootstrapServers = kafkaConfigService.getBootstrapServers();
        if (adminClient == null || !bootstrapServers.equals(adminClientBootstrapServers)) {
            if (adminClient != null) {
                log.info("Kafka bootstrap servers changed from {} to {}, recreating admin client",
                        adminClientBootstrapServers, bootstrapServers);
                adminClient.close(CLOSE_TIMEOUT);
            }
            Map<String, Object> props = new HashMap<>();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            adminClient = AdminClient.create(props);
            adminClientBootstrapServers = bootstrapServers;
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
