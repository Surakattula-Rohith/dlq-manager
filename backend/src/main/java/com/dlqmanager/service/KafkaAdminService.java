package com.dlqmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.TopicListing;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.*;
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

    private final KafkaAdmin kafkaAdmin;

    /**
     * List all Kafka topics in the cluster
     *
     * @return List of topic names
     * @throws RuntimeException if connection to Kafka fails
     */
    public List<String> listAllTopics() {
        log.debug("Fetching all Kafka topics...");

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
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

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
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

        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
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
}
