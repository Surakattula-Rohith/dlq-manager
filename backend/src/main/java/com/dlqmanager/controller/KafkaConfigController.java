package com.dlqmanager.controller;

import com.dlqmanager.model.entity.KafkaConfig;
import com.dlqmanager.service.KafkaConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/kafka/config")
@RequiredArgsConstructor
@Slf4j
public class KafkaConfigController {

    private final KafkaConfigService kafkaConfigService;

    /**
     * GET /api/kafka/config - Get current Kafka configuration
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        log.info("API: Getting Kafka config");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("bootstrapServers", kafkaConfigService.getBootstrapServers());
        response.put("configured", kafkaConfigService.isConfigured());

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/kafka/config - Save/update Kafka configuration
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody Map<String, String> body) {
        log.info("API: Saving Kafka config");

        String bootstrapServers = body.get("bootstrapServers");
        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "bootstrapServers is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            KafkaConfig config = kafkaConfigService.saveConfig(bootstrapServers);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("bootstrapServers", config.getBootstrapServers());
            response.put("configured", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to save Kafka config", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * POST /api/kafka/config/test - Test connection to given bootstrap servers
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody Map<String, String> body) {
        log.info("API: Testing Kafka connection");

        String bootstrapServers = body.get("bootstrapServers");
        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "bootstrapServers is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Map<String, Object> result = kafkaConfigService.testConnection(bootstrapServers);
        return ResponseEntity.ok(result);
    }
}
