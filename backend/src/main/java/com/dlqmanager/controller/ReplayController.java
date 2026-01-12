package com.dlqmanager.controller;

import com.dlqmanager.model.dto.BulkReplayRequestDto;
import com.dlqmanager.model.dto.ReplayJobDto;
import com.dlqmanager.model.dto.ReplayRequestDto;
import com.dlqmanager.service.ReplayService;
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

/**
 * REST Controller for message replay operations
 *
 * Endpoints:
 * - POST /api/replay/single: Replay one message
 * - GET /api/replay/jobs/{id}: Get replay job status
 * - GET /api/replay/history: Get all replay jobs
 * - GET /api/replay/history/dlq/{dlqTopicId}: Get history for specific DLQ
 */
@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
@Slf4j
public class ReplayController {

    private final ReplayService replayService;

    /**
     * Replay a single message from DLQ to source topic
     *
     * POST /api/replay/single
     *
     * Request body:
     * {
     *   "dlqTopicId": "abc-123-uuid",
     *   "messageOffset": 42,
     *   "messagePartition": 0,
     *   "initiatedBy": "admin@example.com"
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "Message replayed successfully",
     *   "replayJob": { ... job details ... }
     * }
     *
     * @param request validated replay request
     * @return ReplayJobDto with 200 OK on success, error response on failure
     */
    @PostMapping("/single")
    public ResponseEntity<Map<String, Object>> replaySingleMessage(
            @Valid @RequestBody ReplayRequestDto request) {

        log.info("API: POST /api/replay/single - DLQ: {}, offset: {}, partition: {}",
                request.getDlqTopicId(), request.getMessageOffset(), request.getMessagePartition());

        try {
            // Replay the message
            ReplayJobDto replayJob = replayService.replayMessage(request);

            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Message replayed successfully");
            response.put("replayJob", replayJob);

            log.info("Successfully replayed message. Job ID: {}", replayJob.getId());
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Failed to replay message", e);
            return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during replay", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Replay multiple messages from DLQ to source topic (Bulk Replay)
     *
     * POST /api/replay/bulk
     *
     * Request body:
     * {
     *   "dlqTopicId": "abc-123-uuid",
     *   "messages": [
     *     {"offset": 51, "partition": 0},
     *     {"offset": 52, "partition": 0},
     *     {"offset": 53, "partition": 0}
     *   ],
     *   "initiatedBy": "user@example.com"
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "Bulk replay completed",
     *   "replayJob": {
     *     "id": "job-uuid",
     *     "totalMessages": 3,
     *     "succeeded": 3,
     *     "failed": 0,
     *     "status": "COMPLETED"
     *   }
     * }
     *
     * @param request validated bulk replay request
     * @return ReplayJobDto with 200 OK on success, error response on failure
     */
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkReplayMessages(
            @Valid @RequestBody BulkReplayRequestDto request) {

        log.info("API: POST /api/replay/bulk - DLQ: {}, message count: {}",
                request.getDlqTopicId(), request.getMessages().size());

        try {
            // Replay the messages
            ReplayJobDto replayJob = replayService.bulkReplayMessages(request);

            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);

            // Dynamic message based on results
            String message;
            if (replayJob.getFailed() == 0) {
                message = String.format("All %d messages replayed successfully", replayJob.getSucceeded());
            } else if (replayJob.getSucceeded() == 0) {
                message = String.format("All %d messages failed to replay", replayJob.getFailed());
            } else {
                message = String.format("Bulk replay completed: %d succeeded, %d failed",
                        replayJob.getSucceeded(), replayJob.getFailed());
            }
            response.put("message", message);
            response.put("replayJob", replayJob);

            log.info("Bulk replay completed. Job ID: {}, Succeeded: {}, Failed: {}",
                    replayJob.getId(), replayJob.getSucceeded(), replayJob.getFailed());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Failed to execute bulk replay", e);
            return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during bulk replay", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Get replay job by ID
     *
     * GET /api/replay/jobs/{id}
     *
     * Response:
     * {
     *   "success": true,
     *   "replayJob": { ... job details ... }
     * }
     *
     * @param jobId UUID of the replay job
     * @return ReplayJobDto with 200 OK, or 404 if not found
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<Map<String, Object>> getReplayJob(@PathVariable("id") UUID jobId) {
        log.info("API: GET /api/replay/jobs/{}", jobId);

        try {
            ReplayJobDto replayJob = replayService.getReplayJob(jobId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("replayJob", replayJob);

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Replay job not found: {}", jobId, e);
            return createErrorResponse(HttpStatus.NOT_FOUND, "Replay job not found: " + jobId);

        } catch (Exception e) {
            log.error("Error fetching replay job: {}", jobId, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Get replay history (all jobs)
     *
     * GET /api/replay/history
     *
     * Response:
     * {
     *   "success": true,
     *   "count": 15,
     *   "replayJobs": [ ... list of jobs ... ]
     * }
     *
     * @return List of ReplayJobDto, newest first
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getReplayHistory() {
        log.info("API: GET /api/replay/history");

        try {
            List<ReplayJobDto> jobs = replayService.getReplayHistory();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", jobs.size());
            response.put("replayJobs", jobs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching replay history", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Get replay history for a specific DLQ topic
     *
     * GET /api/replay/history/dlq/{dlqTopicId}
     *
     * Response:
     * {
     *   "success": true,
     *   "count": 5,
     *   "replayJobs": [ ... list of jobs for this DLQ ... ]
     * }
     *
     * @param dlqTopicId UUID of the DLQ topic
     * @return List of ReplayJobDto for that DLQ
     */
    @GetMapping("/history/dlq/{dlqTopicId}")
    public ResponseEntity<Map<String, Object>> getReplayHistoryForDlq(
            @PathVariable("dlqTopicId") UUID dlqTopicId) {

        log.info("API: GET /api/replay/history/dlq/{}", dlqTopicId);

        try {
            List<ReplayJobDto> jobs = replayService.getReplayHistoryForDlq(dlqTopicId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("dlqTopicId", dlqTopicId);
            response.put("count", jobs.size());
            response.put("replayJobs", jobs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching replay history for DLQ: {}", dlqTopicId, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Create error response with consistent structure
     *
     * @param status HTTP status code
     * @param message Error message
     * @return ResponseEntity with error details
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("status", status.value());

        return ResponseEntity.status(status).body(response);
    }
}
