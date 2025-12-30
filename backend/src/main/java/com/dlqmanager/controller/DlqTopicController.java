package com.dlqmanager.controller;

import com.dlqmanager.model.dto.DlqTopicResponse;
import com.dlqmanager.model.dto.RegisterDlqRequest;
import com.dlqmanager.model.dto.UpdateDlqRequest;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.service.DlqDiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for managing DLQ topic registrations
 * Provides CRUD operations for DLQ topics
 */
@RestController
@RequestMapping("/api/dlq-topics")
@RequiredArgsConstructor
@Slf4j
public class DlqTopicController {

    private final DlqDiscoveryService dlqDiscoveryService;

    /**
     * List all registered DLQ topics
     *
     * GET /api/dlq-topics
     *
     * @return List of all DLQ topics with 200 OK
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDlqTopics() {
        log.info("API: GET /api/dlq-topics - Listing all DLQ topics");

        try {
            List<DlqTopic> dlqTopics = dlqDiscoveryService.getAllDlqTopics();

            // Convert entities to DTOs
            List<DlqTopicResponse> responses = dlqTopics.stream()
                .map(DlqTopicResponse::fromEntity)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", responses.size());
            response.put("dlqTopics", responses);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to list DLQ topics", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Register a new DLQ topic
     *
     * POST /api/dlq-topics
     *
     * @param request The registration request (validated)
     * @return The registered DLQ topic with 201 Created
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> registerDlqTopic(@Valid @RequestBody RegisterDlqRequest request) {
        log.info("API: POST /api/dlq-topics - Registering DLQ: {} -> {}",
            request.getDlqTopicName(), request.getSourceTopic());

        try {
            DlqTopic dlqTopic = dlqDiscoveryService.registerDlqTopic(request);
            DlqTopicResponse responseDto = DlqTopicResponse.fromEntity(dlqTopic);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "DLQ topic registered successfully");
            response.put("dlqTopic", responseDto);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Validation failed: {}", e.getMessage());
            return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to register DLQ topic", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Get a specific DLQ topic by ID
     *
     * GET /api/dlq-topics/{id}
     *
     * @param id The UUID of the DLQ topic
     * @return The DLQ topic with 200 OK, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDlqTopicById(@PathVariable UUID id) {
        log.info("API: GET /api/dlq-topics/{} - Fetching DLQ topic", id);

        try {
            DlqTopic dlqTopic = dlqDiscoveryService.getDlqTopicById(id);
            DlqTopicResponse responseDto = DlqTopicResponse.fromEntity(dlqTopic);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("dlqTopic", responseDto);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("DLQ topic not found: {}", id);
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to fetch DLQ topic: {}", id, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Update an existing DLQ topic
     *
     * PUT /api/dlq-topics/{id}
     *
     * @param id The UUID of the DLQ topic to update
     * @param request The update request
     * @return The updated DLQ topic with 200 OK, or 404 Not Found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateDlqTopic(
        @PathVariable UUID id,
        @RequestBody UpdateDlqRequest request
    ) {
        log.info("API: PUT /api/dlq-topics/{} - Updating DLQ topic", id);

        try {
            DlqTopic dlqTopic = dlqDiscoveryService.updateDlqTopic(id, request);
            DlqTopicResponse responseDto = DlqTopicResponse.fromEntity(dlqTopic);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "DLQ topic updated successfully");
            response.put("dlqTopic", responseDto);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Update failed: {}", e.getMessage());
            return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to update DLQ topic: {}", id, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Delete a DLQ topic registration
     *
     * DELETE /api/dlq-topics/{id}
     *
     * @param id The UUID of the DLQ topic to delete
     * @return Success message with 200 OK, or 404 Not Found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDlqTopic(@PathVariable UUID id) {
        log.info("API: DELETE /api/dlq-topics/{} - Deleting DLQ topic", id);

        try {
            dlqDiscoveryService.deleteDlqTopic(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "DLQ topic deleted successfully");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Delete failed: {}", e.getMessage());
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to delete DLQ topic: {}", id, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Get only active DLQ topics
     *
     * GET /api/dlq-topics/active
     *
     * @return List of active DLQ topics with 200 OK
     */
    @GetMapping("/filter/active")
    public ResponseEntity<Map<String, Object>> getActiveDlqTopics() {
        log.info("API: GET /api/dlq-topics/filter/active - Listing active DLQ topics");

        try {
            List<DlqTopic> dlqTopics = dlqDiscoveryService.getActiveDlqTopics();

            List<DlqTopicResponse> responses = dlqTopics.stream()
                .map(DlqTopicResponse::fromEntity)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", responses.size());
            response.put("dlqTopics", responses);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to list active DLQ topics", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Helper method to create error responses
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", message);

        return ResponseEntity.status(status).body(errorResponse);
    }
}
