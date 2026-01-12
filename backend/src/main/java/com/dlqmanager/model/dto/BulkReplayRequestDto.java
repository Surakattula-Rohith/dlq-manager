package com.dlqmanager.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * DTO for bulk message replay request
 * Used in POST /api/replay/bulk request body
 *
 * Purpose: Replay multiple messages from DLQ in a single operation
 *
 * Example:
 * {
 *   "dlqTopicId": "abc-123-uuid",
 *   "messages": [
 *     {"offset": 51, "partition": 0},
 *     {"offset": 52, "partition": 0},
 *     {"offset": 53, "partition": 0}
 *   ],
 *   "initiatedBy": "user@example.com"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkReplayRequestDto {

    /**
     * UUID of the DLQ topic to replay from
     */
    @NotNull(message = "DLQ topic ID is required")
    private UUID dlqTopicId;

    /**
     * List of messages to replay
     * Each message is identified by offset and partition
     */
    @NotEmpty(message = "Messages list cannot be empty")
    @Valid
    private List<MessageIdentifier> messages;

    /**
     * Optional: Who is initiating this bulk replay
     */
    private String initiatedBy;

    /**
     * Inner class to identify a message by offset and partition
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageIdentifier {

        @NotNull(message = "Message offset is required")
        @Min(value = 0, message = "Message offset must be >= 0")
        private Long offset;

        @NotNull(message = "Message partition is required")
        @Min(value = 0, message = "Message partition must be >= 0")
        private Integer partition;
    }
}
