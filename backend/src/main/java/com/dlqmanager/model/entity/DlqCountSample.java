package com.dlqmanager.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DLQ Count Sample
 *
 * A snapshot of a DLQ topic's message counts, taken by the alert evaluator every minute.
 *
 * Why we need this:
 * - Time-window alerts ask "did N new messages arrive in the last X minutes?"
 * - To answer that we need to know what the count was X minutes ago
 * - Keeping one row per topic per minute gives us that history (old rows are pruned)
 *
 * dlqTopicId is a plain column (not a relation) on purpose:
 * samples are disposable history and must never block deleting a DLQ topic.
 */
@Entity
@Table(
        name = "dlq_count_samples",
        indexes = @Index(name = "idx_dlq_count_samples_topic_time", columnList = "dlq_topic_id, sampled_at")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlqCountSample {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dlq_topic_id", nullable = false)
    private UUID dlqTopicId;

    /**
     * Messages still waiting to be replayed
     */
    @Column(name = "pending_count", nullable = false)
    private Long pendingCount;

    /**
     * Sum of the end offsets of all partitions.
     * Only ever goes up (every new DLQ message adds 1), even when Kafka
     * deletes old messages - so the difference between two samples is
     * exactly the number of new failures in between.
     */
    @Column(name = "end_offset_sum", nullable = false)
    private Long endOffsetSum;

    @Column(name = "sampled_at", nullable = false)
    private LocalDateTime sampledAt;
}
