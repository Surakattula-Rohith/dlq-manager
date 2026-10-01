package com.dlqmanager.repository;

import com.dlqmanager.model.entity.ReplayClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface ReplayClaimRepository extends JpaRepository<ReplayClaim, UUID> {

    /**
     * Claim a message: 1 if this caller got it, 0 if someone else already holds it.
     *
     * ON CONFLICT DO NOTHING turns "is it free? then take it" into one atomic step,
     * so two requests at the same moment can never both get 1.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO replay_claims (id, dlq_topic_id, dlq_partition, dlq_offset, claimed_by, claimed_at) "
            + "VALUES (:id, :dlqTopicId, :partition, :offset, :claimedBy, :claimedAt) "
            + "ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int tryInsert(@Param("id") UUID id,
                  @Param("dlqTopicId") UUID dlqTopicId,
                  @Param("partition") int partition,
                  @Param("offset") long offset,
                  @Param("claimedBy") String claimedBy,
                  @Param("claimedAt") LocalDateTime claimedAt);

    /**
     * Remove a claim on this message that is older than the cutoff (left behind by a crash)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ReplayClaim c WHERE c.dlqTopicId = :dlqTopicId AND c.dlqPartition = :partition "
            + "AND c.dlqOffset = :offset AND c.claimedAt < :cutoff")
    int deleteAbandoned(@Param("dlqTopicId") UUID dlqTopicId,
                        @Param("partition") int partition,
                        @Param("offset") long offset,
                        @Param("cutoff") LocalDateTime cutoff);
}
