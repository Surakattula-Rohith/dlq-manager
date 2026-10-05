package com.dlqmanager.controller;

import com.dlqmanager.model.dto.DlqMessageDto;
import com.dlqmanager.model.dto.DlqTopicResponse;
import com.dlqmanager.model.dto.ErrorBreakdownDto;
import com.dlqmanager.model.dto.RegisterDlqRequest;
import com.dlqmanager.model.dto.UpdateDlqRequest;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.TrendRange;
import com.dlqmanager.service.DlqBrowserService;
import com.dlqmanager.service.DlqDiscoveryService;
import com.dlqmanager.service.DlqTrendService;
import com.dlqmanager.service.MessageExportWriter;
import com.dlqmanager.service.MessageFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DlqDiscoveryService dlqDiscoveryService;
    private final DlqBrowserService dlqBrowserService;
    private final MessageExportWriter messageExportWriter;
    private final DlqTrendService dlqTrendService;

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
     * Browse messages from a DLQ topic with pagination, optionally filtered
     *
     * GET /api/dlq-topics/{id}/messages?page=1&size=10&search=ORD-1&errorType=DB%20Connection%20Timeout&pendingOnly=true
     *
     * Purpose: Fetch messages from the DLQ for viewing
     *
     * Query Parameters:
     * - page: Page number (1-based, default: 1)
     * - size: Messages per page (default: 10, max: 100)
     * - search: Optional text to find in the key, payload or headers (case-insensitive)
     * - errorType: Optional exact error type from the error breakdown
     * - pendingOnly: Optional, true hides messages that were already replayed
     *
     * @param id The UUID of the DLQ topic
     * @param page Page number (optional, default 1)
     * @param size Page size (optional, default 10)
     * @return List of messages with pagination info
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String errorType,
        @RequestParam(defaultValue = "false") boolean pendingOnly
    ) {
        log.info("API: GET /api/dlq-topics/{}/messages?page={}&size={}", id, page, size);

        // Validate pagination parameters
        if (page < 1) {
            return createErrorResponse(HttpStatus.BAD_REQUEST, "Page number must be >= 1");
        }
        if (size < 1 || size > 100) {
            return createErrorResponse(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
        }
        if (search != null && search.length() > 200) {
            return createErrorResponse(HttpStatus.BAD_REQUEST, "Search text must be at most 200 characters");
        }

        try {
            MessageFilter filter = new MessageFilter(search, errorType, pendingOnly);

            // Get counts for pagination metadata
            // total = everything stored in Kafka (all browsable), pending = not yet replayed
            DlqBrowserService.MessageCounts counts = dlqBrowserService.getMessageCounts(id);

            List<DlqMessageDto> messages;
            long matchingMessages;
            boolean scanLimitReached = false;

            if (filter.isActive()) {
                // Filtered: every message has to be checked
                DlqBrowserService.SearchResult result = dlqBrowserService.searchMessages(id, filter, page, size);
                messages = result.messages();
                matchingMessages = result.matching();
                scanLimitReached = result.scanLimitReached();
            } else {
                // Unfiltered: jump straight to the page
                messages = dlqBrowserService.getMessages(id, page, size);
                matchingMessages = counts.total();
            }

            int totalPages = (int) Math.ceil((double) matchingMessages / size);

            // Build response with pagination metadata
            Map<String, Object> pagination = new LinkedHashMap<>();
            pagination.put("currentPage", page);
            pagination.put("pageSize", size);
            pagination.put("totalMessages", counts.total());
            pagination.put("pendingMessages", counts.pending());
            pagination.put("replayedMessages", counts.replayed());
            pagination.put("matchingMessages", matchingMessages);
            pagination.put("filtered", filter.isActive());
            pagination.put("scanLimitReached", scanLimitReached);
            pagination.put("totalPages", totalPages);
            pagination.put("hasNextPage", page < totalPages);
            pagination.put("hasPreviousPage", page > 1);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("messages", messages);
            response.put("pagination", pagination);

            log.info("Successfully fetched {} messages for DLQ topic {}", messages.size(), id);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("DLQ topic not found: {}", id);
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to fetch messages for DLQ topic: {}", id, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to fetch messages: " + e.getMessage());
        }
    }

    /**
     * Download messages as CSV or JSON
     *
     * GET /api/dlq-topics/{id}/messages/export?format=csv&search=...&errorType=...&pendingOnly=true
     *
     * Takes the same filters as the message browser, so the download contains exactly
     * what is on screen (all pages, not just the current one). Messages are streamed
     * to the response one by one, so a big DLQ never has to fit in memory.
     *
     * @param id     The UUID of the DLQ topic
     * @param format "csv" (default) or "json"
     * @return a file download
     */
    @GetMapping("/{id}/messages/export")
    public ResponseEntity<StreamingResponseBody> exportMessages(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "csv") String format,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String errorType,
        @RequestParam(defaultValue = "false") boolean pendingOnly
    ) {
        log.info("API: GET /api/dlq-topics/{}/messages/export?format={}", id, format);

        // The return type must stay ResponseEntity<StreamingResponseBody> for Spring to stream it,
        // so errors are reported with ResponseStatusException instead of an error body
        boolean csv = "csv".equalsIgnoreCase(format);
        if (!csv && !"json".equalsIgnoreCase(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be csv or json");
        }

        DlqTopic dlqTopic;
        try {
            dlqTopic = dlqDiscoveryService.getDlqTopicById(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        MessageFilter filter = new MessageFilter(search, errorType, pendingOnly);
        String fileName = dlqTopic.getDlqTopicName() + "-" + EXPORT_TIMESTAMP.format(LocalDateTime.now())
                + (csv ? ".csv" : ".json");

        StreamingResponseBody body = outputStream -> {
            Writer out = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            boolean[] first = {true};

            if (csv) {
                messageExportWriter.writeCsvHeader(out);
            } else {
                messageExportWriter.writeJsonStart(out);
            }

            dlqBrowserService.forEachMatchingMessage(id, filter, message -> {
                try {
                    if (csv) {
                        messageExportWriter.writeCsvRow(out, message);
                    } else {
                        messageExportWriter.writeJsonItem(out, message, first[0]);
                        first[0] = false;
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            if (!csv) {
                messageExportWriter.writeJsonEnd(out);
            }
            out.flush();
        };

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(fileName).build().toString())
            .contentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : MediaType.APPLICATION_JSON)
            .body(body);
    }

    /**
     * Get total message count for a DLQ topic
     *
     * GET /api/dlq-topics/{id}/message-count
     *
     * Purpose: Useful for displaying total messages without fetching all of them
     *
     * @param id The UUID of the DLQ topic
     * @return Total message count
     */
    @GetMapping("/{id}/message-count")
    public ResponseEntity<Map<String, Object>> getMessageCount(@PathVariable UUID id) {
        log.info("API: GET /api/dlq-topics/{}/message-count", id);

        try {
            DlqBrowserService.MessageCounts counts = dlqBrowserService.getMessageCounts(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalMessages", counts.total());
            response.put("pendingMessages", counts.pending());
            response.put("replayedMessages", counts.replayed());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("DLQ topic not found: {}", id);
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to get message count for DLQ topic: {}", id, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to get message count: " + e.getMessage());
        }
    }

    /**
     * Get error breakdown statistics for a DLQ topic
     *
     * GET /api/dlq-topics/{id}/error-breakdown
     *
     * Purpose: Analyze all messages and show breakdown by error type
     *
     * This endpoint helps answer:
     * - What are the most common errors in this DLQ?
     * - What percentage of failures are due to each error type?
     * - Which errors should we prioritize fixing?
     *
     * Response includes:
     * - Total message count
     * - List of error types with count and percentage
     * - Sorted by count (most common errors first)
     *
     * @param id The UUID of the DLQ topic
     * @return Error breakdown statistics
     */
    @GetMapping("/{id}/error-breakdown")
    public ResponseEntity<Map<String, Object>> getErrorBreakdown(@PathVariable UUID id) {
        log.info("API: GET /api/dlq-topics/{}/error-breakdown", id);

        try {
            // Get error counts from service
            Map<String, Long> errorCounts = dlqBrowserService.getErrorBreakdown(id);

            // Calculate total messages
            long totalMessages = errorCounts.values().stream()
                    .mapToLong(Long::longValue)
                    .sum();

            // Convert to DTO list with percentages
            List<ErrorBreakdownDto> errorBreakdown = errorCounts.entrySet().stream()
                    .map(entry -> {
                        String errorType = entry.getKey();
                        Long count = entry.getValue();
                        Double percentage = (count * 100.0) / totalMessages;
                        return new ErrorBreakdownDto(errorType, count, percentage);
                    })
                    .sorted(Comparator.comparing(ErrorBreakdownDto::getCount).reversed()) // Sort by count descending
                    .collect(Collectors.toList());

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalMessages", totalMessages);
            response.put("errorBreakdown", errorBreakdown);

            log.info("Successfully generated error breakdown for DLQ topic {}. Total messages: {}, Distinct errors: {}",
                    id, totalMessages, errorBreakdown.size());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("DLQ topic not found: {}", id);
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to get error breakdown for DLQ topic: {}", id, e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to get error breakdown: " + e.getMessage());
        }
    }

    /**
     * How a DLQ developed over time (trend chart)
     *
     * GET /api/dlq-topics/{id}/trend?range=24h   (24 hourly points)
     * GET /api/dlq-topics/{id}/trend?range=7d    (28 points, one per 6 hours)
     *
     * Each point: time (start, UTC), pending (waiting at the end of it) and newMessages
     * (arrived during it). Both are null where no history exists yet.
     */
    @GetMapping("/{id}/trend")
    public ResponseEntity<Map<String, Object>> getTrend(@PathVariable UUID id,
                                                        @RequestParam(defaultValue = "24h") String range) {
        Optional<TrendRange> trendRange = TrendRange.fromCode(range);
        if (trendRange.isEmpty()) {
            return createErrorResponse(HttpStatus.BAD_REQUEST, "range must be 24h or 7d");
        }

        try {
            List<Map<String, Object>> points = dlqTrendService.getTrend(id, trendRange.get()).stream()
                    .map(point -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        // The app runs in UTC (see DlqManagerApplication); the browser shows local time
                        m.put("time", point.start().toInstant(ZoneOffset.UTC).toString());
                        m.put("pending", point.pending());
                        m.put("newMessages", point.newMessages());
                        return m;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("range", trendRange.get().code());
            response.put("bucketMinutes", trendRange.get().bucketMinutes());
            response.put("points", points);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
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
