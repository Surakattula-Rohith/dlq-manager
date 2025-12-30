package com.dlqmanager.controller;

import com.dlqmanager.service.KafkaAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Kafka-related operations
 * Provides endpoints to interact with Kafka cluster
 */
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@Slf4j
public class KafkaController {

    private final KafkaAdminService kafkaAdminService;

    /**
     * List all Kafka topics in the cluster
     *
     * GET /api/kafka/topics
     *
     * @return JSON response with list of topic names
     */
    @GetMapping("/topics")
    public ResponseEntity<Map<String, Object>> listTopics() {
        log.info("API: Listing all Kafka topics");

        try {
            List<String> topics = kafkaAdminService.listAllTopics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", topics.size());
            response.put("topics", topics);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to list Kafka topics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Check if a specific topic exists
     *
     * GET /api/kafka/topics/{topicName}/exists
     *
     * @param topicName The name of the topic to check
     * @return JSON response with exists boolean
     */
    @GetMapping("/topics/{topicName}/exists")
    public ResponseEntity<Map<String, Object>> topicExists(@PathVariable String topicName) {
        log.info("API: Checking if topic exists: {}", topicName);

        try {
            boolean exists = kafkaAdminService.topicExists(topicName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("topicName", topicName);
            response.put("exists", exists);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to check topic existence: {}", topicName, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Auto-discover DLQ topics based on naming conventions
     *
     * GET /api/kafka/discover-dlqs
     *
     * @return JSON response with discovered DLQ -> Source topic mappings
     */
    @GetMapping("/discover-dlqs")
    public ResponseEntity<Map<String, Object>> discoverDlqs() {
        log.info("API: Auto-discovering DLQ topics");

        try {
            Map<String, String> dlqMappings = kafkaAdminService.discoverDlqTopics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", dlqMappings.size());
            response.put("dlqMappings", dlqMappings);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to discover DLQ topics", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get Kafka cluster information
     *
     * GET /api/kafka/cluster-info
     *
     * @return JSON response with cluster details
     */
    @GetMapping("/cluster-info")
    public ResponseEntity<Map<String, Object>> getClusterInfo() {
        log.info("API: Getting Kafka cluster information");

        try {
            Map<String, Object> clusterInfo = kafkaAdminService.getClusterInfo();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("cluster", clusterInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get cluster information", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
