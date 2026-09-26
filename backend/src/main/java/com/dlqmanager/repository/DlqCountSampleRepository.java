package com.dlqmanager.repository;

import com.dlqmanager.model.entity.DlqCountSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DlqCountSampleRepository extends JpaRepository<DlqCountSample, UUID> {

    /**
     * Oldest sample for a topic taken at or after the given time
     * Used as the baseline for time-window alerts ("count at the start of the window")
     */
    Optional<DlqCountSample> findFirstByDlqTopicIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(
            UUID dlqTopicId, LocalDateTime from);

    /**
     * Remove old samples so the table doesn't grow forever
     */
    @Modifying
    @Query("DELETE FROM DlqCountSample s WHERE s.sampledAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    void deleteByDlqTopicId(UUID dlqTopicId);
}
