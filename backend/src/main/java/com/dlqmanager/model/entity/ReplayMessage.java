package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.ReplayMessageStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a single message replay attempt
 * Maps to the 'replay_messages' table in PostgreSQL
 *
 * Purpose: Track individual message results within a replay job
 * Especially important for bulk replays to identify which specific messages failed
 *
 * Example: Bulk replay of 100 messages
 * - 1 ReplayJob record (overall operation)
 * - 100 ReplayMessage records (one per message)
 */
@Entity
@Table(name = "replay_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplayMessage {

    /**
     * Primary key - auto-generated UUID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Reference to the parent replay job
     * Foreign key to replay_jobs table
     * Many messages belong to one job
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replay_job_id", nullable = false)
    private ReplayJob replayJob;

    /**
     * The message key from Kafka (if present)
     * Example: "ORD-12345", "USER-789"
     * Can be null if original message had no key
     * Useful for identifying specific failed messages
     */
    @Column(name = "message_key")
    private String messageKey;

    /**
     * The offset of this message in the DLQ topic
     * Identifies exact position in the topic
     * Used to retrieve the message for replay
     */
    @Column(name = "dlq_offset", nullable = false)
    private Long dlqOffset;

    /**
     * The partition of this message in the DLQ topic
     * Usually 0 for topics with single partition
     * Combined with offset, uniquely identifies a message
     */
    @Column(name = "dlq_partition", nullable = false)
    private Integer dlqPartition;

    /**
     * Status of this individual message replay
     * SUCCESS: Message was successfully sent to source topic
     * FAILED: Failed to send (Kafka error, timeout, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReplayMessageStatus status;

    /**
     * Error message if replay failed
     * Examples:
     * - "Kafka timeout after 30s"
     * - "Topic 'orders' does not exist"
     * - "Serialization error: Invalid UTF-8"
     * Null if status is SUCCESS
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * When this message was replayed (or attempted)
     * Set when replay operation completes (success or failure)
     */
    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;

    /**
     * When this record was created in database
     * Automatically set by Hibernate on first save
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if this message replay was successful
     *
     * @return true if status is SUCCESS
     */
    public boolean isSuccess() {
        return status == ReplayMessageStatus.SUCCESS;
    }

    /**
     * Get a human-readable identifier for this message
     * Uses message key if available, otherwise shows offset/partition
     *
     * @return String like "ORD-12345" or "offset:42,partition:0"
     */
    public String getMessageIdentifier() {
        if (messageKey != null && !messageKey.isEmpty()) {
            return messageKey;
        }
        return String.format("offset:%d,partition:%d", dlqOffset, dlqPartition);
    }
}
