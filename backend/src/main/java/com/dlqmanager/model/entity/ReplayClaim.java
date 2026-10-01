package com.dlqmanager.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Replay Claim
 *
 * Marks one DLQ message as "being replayed right now".
 *
 * The unique constraint on (topic, partition, offset) means only one replay can hold
 * a message at a time - across all users and all running copies of the app - so two
 * people pressing Replay at the same moment can't both send it.
 *
 * A claim is removed as soon as its replay finishes. One left behind by a crash
 * counts as abandoned after a few minutes (see ReplayClaimService).
 */
@Entity
@Table(
        name = "replay_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_replay_claims_message",
                columnNames = {"dlq_topic_id", "dlq_partition", "dlq_offset"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplayClaim {

    @Id
    private UUID id;

    @Column(name = "dlq_topic_id", nullable = false)
    private UUID dlqTopicId;

    @Column(name = "dlq_partition", nullable = false)
    private Integer dlqPartition;

    @Column(name = "dlq_offset", nullable = false)
    private Long dlqOffset;

    @Column(name = "claimed_by", nullable = false, length = 100)
    private String claimedBy;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;
}
