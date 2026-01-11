package com.dlqmanager.model.dto;

import com.dlqmanager.model.entity.ReplayJob;
import com.dlqmanager.model.enums.ReplayStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for ReplayJob response
 * Used when returning replay job information to the client
 *
 * Purpose: Send replay job details without exposing entity internals
 * Includes computed fields like successRate and durationSeconds
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplayJobDto {

    /**
     * Unique identifier for this replay job
     */
    private UUID id;

    /**
     * UUID of the DLQ topic being replayed from
     */
    private UUID dlqTopicId;

    /**
     * Name of the DLQ topic (for convenience, avoid extra lookup)
     */
    private String dlqTopicName;

    /**
     * Name of the source topic (where messages are sent)
     */
    private String sourceTopic;

    /**
     * Who initiated this replay
     */
    private String initiatedBy;

    /**
     * Current status: PENDING, RUNNING, COMPLETED, FAILED
     */
    private ReplayStatus status;

    /**
     * Total number of messages to replay
     */
    private Integer totalMessages;

    /**
     * Count of successfully replayed messages
     */
    private Integer succeeded;

    /**
     * Count of failed messages
     */
    private Integer failed;

    /**
     * Success rate as percentage (0-100)
     * Null if no messages processed yet
     */
    private Double successRate;

    /**
     * Duration of replay in seconds
     * Null if not started or not completed
     */
    private Long durationSeconds;

    /**
     * When job was created
     */
    private LocalDateTime createdAt;

    /**
     * When job started processing
     */
    private LocalDateTime startedAt;

    /**
     * When job finished
     */
    private LocalDateTime completedAt;

    /**
     * Convert ReplayJob entity to ReplayJobDto
     * Static factory method pattern
     *
     * @param entity ReplayJob entity from database
     * @return ReplayJobDto for API response
     */
    public static ReplayJobDto fromEntity(ReplayJob entity) {
        return new ReplayJobDto(
                entity.getId(),
                entity.getDlqTopic().getId(),
                entity.getDlqTopic().getDlqTopicName(),
                entity.getDlqTopic().getSourceTopic(),
                entity.getInitiatedBy(),
                entity.getStatus(),
                entity.getTotalMessages(),
                entity.getSucceeded(),
                entity.getFailed(),
                entity.getSuccessRate(),        // Computed method from entity
                entity.getDurationSeconds(),    // Computed method from entity
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }
}
