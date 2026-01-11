package com.dlqmanager.repository;

import com.dlqmanager.model.entity.ReplayJob;
import com.dlqmanager.model.enums.ReplayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ReplayJob entity
 * Spring Data JPA automatically implements all methods at runtime
 *
 * No SQL code needed - Spring generates queries from method names!
 */
@Repository
public interface ReplayJobRepository extends JpaRepository<ReplayJob, UUID> {

    /**
     * Find all replay jobs with a specific status
     * SQL: SELECT * FROM replay_jobs WHERE status = ?
     * Useful for: Getting all running jobs, all completed jobs, etc.
     *
     * @param status the status to filter by (PENDING, RUNNING, COMPLETED, FAILED)
     * @return list of ReplayJobs with the given status
     */
    List<ReplayJob> findByStatus(ReplayStatus status);

    /**
     * Find all replay jobs for a specific DLQ topic
     * SQL: SELECT * FROM replay_jobs WHERE dlq_topic_id = ?
     * Useful for: Viewing replay history for a specific DLQ
     *
     * @param dlqTopicId the UUID of the DLQ topic
     * @return list of ReplayJobs for the given DLQ
     */
    List<ReplayJob> findByDlqTopicId(UUID dlqTopicId);

    /**
     * Find all replay jobs ordered by creation time (newest first)
     * SQL: SELECT * FROM replay_jobs ORDER BY created_at DESC
     * Useful for: Replay history page showing recent operations
     *
     * @return list of all ReplayJobs ordered by creation time descending
     */
    List<ReplayJob> findAllByOrderByCreatedAtDesc();

    /**
     * Find replay jobs by status, ordered by creation time
     * SQL: SELECT * FROM replay_jobs WHERE status = ? ORDER BY created_at DESC
     * Useful for: Getting recent completed jobs, recent failed jobs, etc.
     *
     * @param status the status to filter by
     * @return list of ReplayJobs with given status, newest first
     */
    List<ReplayJob> findByStatusOrderByCreatedAtDesc(ReplayStatus status);

    /**
     * Find replay jobs initiated by a specific user
     * SQL: SELECT * FROM replay_jobs WHERE initiated_by = ?
     * Useful for: Audit trail - who did what
     *
     * @param initiatedBy username or email
     * @return list of ReplayJobs initiated by the user
     */
    List<ReplayJob> findByInitiatedBy(String initiatedBy);

    /**
     * Count jobs by status
     * SQL: SELECT COUNT(*) FROM replay_jobs WHERE status = ?
     * Useful for: Dashboard metrics (how many running jobs, how many completed, etc.)
     *
     * @param status the status to count
     * @return number of jobs with that status
     */
    long countByStatus(ReplayStatus status);

    /**
     * Find replay jobs for a specific DLQ topic with a specific status
     * SQL: SELECT * FROM replay_jobs WHERE dlq_topic_id = ? AND status = ?
     * Useful for: Finding running replays for a specific DLQ
     *
     * @param dlqTopicId the UUID of the DLQ topic
     * @param status the status to filter by
     * @return list of matching ReplayJobs
     */
    List<ReplayJob> findByDlqTopicIdAndStatus(UUID dlqTopicId, ReplayStatus status);
}
