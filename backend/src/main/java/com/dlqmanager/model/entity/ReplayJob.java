package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.ReplayStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a message replay operation
 * Maps to the 'replay_jobs' table in PostgreSQL
 *
 * Purpose: Track replay operations for audit trail and status monitoring
 *
 * Lifecycle:
 * 1. PENDING: Job created, not started yet
 * 2. RUNNING: Currently replaying messages
 * 3. COMPLETED: All messages processed (check succeeded/failed counts)
 * 4. FAILED: Job failed to execute (system error, not message failure)
 */
@Entity
@Table(name = "replay_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplayJob {

    /**
     * Primary key - auto-generated UUID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Reference to the DLQ topic being replayed from
     * Foreign key to dlq_topics table
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dlq_topic_id", nullable = false)
    private DlqTopic dlqTopic;

    /**
     * Who initiated this replay operation
     * Example: "admin@example.com" or "admin" or "system"
     * For MVP: Can be hardcoded or from request header
     * For production: Should come from authentication token
     */
    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    /**
     * Current status of the replay job
     * PENDING → RUNNING → COMPLETED/FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReplayStatus status;

    /**
     * Total number of messages to replay
     * For single message replay: 1
     * For bulk replay: N (could be hundreds or thousands)
     */
    @Column(name = "total_messages", nullable = false)
    private Integer totalMessages;

    /**
     * Count of messages successfully replayed
     * Incremented as messages are sent successfully
     * Final value: succeeded + failed should equal totalMessages
     */
    @Column(name = "succeeded")
    private Integer succeeded = 0;

    /**
     * Count of messages that failed to replay
     * Incremented when sending to Kafka fails
     */
    @Column(name = "failed")
    private Integer failed = 0;

    /**
     * Optional: Rate limit in messages per second
     * For bulk replays to avoid overwhelming the system
     * Example: 100 means send max 100 messages/second
     * Null = no rate limiting
     */
    @Column(name = "rate_limit")
    private Integer rateLimit;

    /**
     * Optional: Additional configuration options as JSON
     * Example: {"preserveHeaders": true, "deleteAfterReplay": false}
     * Stored as JSON text in database
     * Can be parsed with Jackson ObjectMapper
     */
    @Column(name = "options", columnDefinition = "TEXT")
    private String options;

    /**
     * When the replay job actually started processing
     * Null if status is still PENDING
     * Set when status changes to RUNNING
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * When the replay job finished (successfully or with errors)
     * Null if status is PENDING or RUNNING
     * Set when status changes to COMPLETED or FAILED
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * When this job record was created in database
     * Automatically set by Hibernate on first save
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Calculate duration of replay job in seconds
     * Returns null if job hasn't completed yet
     *
     * @return Duration in seconds, or null if not completed
     */
    public Long getDurationSeconds() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return java.time.Duration.between(startedAt, completedAt).getSeconds();
    }

    /**
     * Check if replay job is complete (either succeeded or failed)
     *
     * @return true if status is COMPLETED or FAILED
     */
    public boolean isComplete() {
        return status == ReplayStatus.COMPLETED || status == ReplayStatus.FAILED;
    }

    /**
     * Get success rate as percentage
     * Example: 24 succeeded out of 25 = 96.0%
     * Returns null if no messages processed yet
     *
     * @return Success rate (0-100), or null if totalMessages is 0
     */
    public Double getSuccessRate() {
        if (totalMessages == null || totalMessages == 0) {
            return null;
        }
        return (succeeded * 100.0) / totalMessages;
    }
}
