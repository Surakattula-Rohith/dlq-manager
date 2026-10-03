package com.dlqmanager.controller;

import com.dlqmanager.model.dto.KafkaConfigRequest;
import com.dlqmanager.model.dto.KafkaConfigView;
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
     *
     * The Kafka password is never part of the response, only "passwordSet".
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        log.info("API: Getting Kafka config");
        return ResponseEntity.ok(toResponse(kafkaConfigService.describe()));
    }

    /**
     * PUT /api/kafka/config - Save/update Kafka configuration
     *
     * Body: bootstrapServers (required), and optionally authentication, encrypted,
     * username, password, caCertificate (see KafkaConfigRequest).
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody KafkaConfigRequest request) {
        log.info("API: Saving Kafka config");

        try {
            return ResponseEntity.ok(toResponse(kafkaConfigService.saveConfig(request)));

        } catch (IllegalArgumentException | IllegalStateException e) {
            // Something is missing in the request, or the server can't store a password yet
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to save Kafka config", e);
            return ResponseEntity.status(500).body(errorResponse(e.getMessage()));
        }
    }

    /**
     * POST /api/kafka/config/test - Test a connection with the given settings (nothing is saved)
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody KafkaConfigRequest request) {
        log.info("API: Testing Kafka connection");

        if (request.bootstrapServers() == null || request.bootstrapServers().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(errorResponse("bootstrapServers is required"));
        }

        return ResponseEntity.ok(kafkaConfigService.testConnection(request));
    }

    private static Map<String, Object> toResponse(KafkaConfigView view) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("bootstrapServers", view.bootstrapServers());
        response.put("configured", view.configured());
        response.put("authentication", view.authentication());
        response.put("encrypted", view.encrypted());
        response.put("username", view.username());
        response.put("passwordSet", view.passwordSet());
        response.put("caCertificate", view.caCertificate());
        response.put("canStorePassword", view.canStorePassword());
        return response;
    }

    private static Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}
