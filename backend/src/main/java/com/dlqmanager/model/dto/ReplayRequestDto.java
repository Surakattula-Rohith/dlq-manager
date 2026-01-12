package com.dlqmanager.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for requesting message replay
 * Used in POST /api/replay/single request body
 *
 * Purpose: Specify which message to replay from which DLQ
 *
 * Example:
 * {
 *   "dlqTopicId": "abc-123-uuid",
 *   "messageOffset": 42,
 *   "messagePartition": 0
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplayRequestDto {

    /**
     * UUID of the DLQ topic to replay from
     * References dlq_topics.id in database
     * Used to look up source topic name
     */
    @NotNull(message = "DLQ topic ID is required")
    private UUID dlqTopicId;

    /**
     * Offset of the message in the DLQ topic
     * Identifies exact position in Kafka partition
     * Example: 42 means the 43rd message (0-indexed)
     */
    @NotNull(message = "Message offset is required")
    @Min(value = 0, message = "Message offset must be >= 0")
    private Long messageOffset;

    /**
     * Partition number of the message
     * Usually 0 for single-partition topics
     * Combined with offset, uniquely identifies a message
     */
    @NotNull(message = "Message partition is required")
    @Min(value = 0, message = "Message partition must be >= 0")
    private Integer messagePartition;

    /**
     * Optional: Who is initiating this replay
     * Example: "admin@example.com", "admin"
     * If null, service will use default: "system"
     */
    private String initiatedBy;
}
