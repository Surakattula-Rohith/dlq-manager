package com.dlqmanager.repository;

import com.dlqmanager.model.entity.ReplayMessage;
import com.dlqmanager.model.enums.ReplayMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ReplayMessage entity
 * Spring Data JPA automatically implements all methods at runtime
 *
 * Tracks individual message replay results within a replay job
 */
@Repository
public interface ReplayMessageRepository extends JpaRepository<ReplayMessage, UUID> {

    /**
     * Find all messages for a specific replay job
     * SQL: SELECT * FROM replay_messages WHERE replay_job_id = ?
     * Useful for: Viewing details of which messages succeeded/failed in a job
     *
     * @param replayJobId the UUID of the replay job
     * @return list of all ReplayMessages for that job
     */
    List<ReplayMessage> findByReplayJobId(UUID replayJobId);

    /**
     * Find messages by job and status
     * SQL: SELECT * FROM replay_messages WHERE replay_job_id = ? AND status = ?
     * Useful for: Getting only failed messages from a job
     *
     * @param replayJobId the UUID of the replay job
     * @param status the status to filter by (SUCCESS or FAILED)
     * @return list of messages matching criteria
     */
    List<ReplayMessage> findByReplayJobIdAndStatus(UUID replayJobId, ReplayMessageStatus status);

    /**
     * Count messages by job and status
     * SQL: SELECT COUNT(*) FROM replay_messages WHERE replay_job_id = ? AND status = ?
     * Useful for: Quickly getting success/failure counts
     *
     * @param replayJobId the UUID of the replay job
     * @param status the status to count
     * @return number of messages with that status
     */
    long countByReplayJobIdAndStatus(UUID replayJobId, ReplayMessageStatus status);

    /**
     * Find all failed messages for a job (convenience method)
     * Same as: findByReplayJobIdAndStatus(jobId, ReplayMessageStatus.FAILED)
     *
     * @param replayJobId the UUID of the replay job
     * @return list of all failed messages
     */
    default List<ReplayMessage> findFailedMessages(UUID replayJobId) {
        return findByReplayJobIdAndStatus(replayJobId, ReplayMessageStatus.FAILED);
    }

    /**
     * Find all successful messages for a job (convenience method)
     * Same as: findByReplayJobIdAndStatus(jobId, ReplayMessageStatus.SUCCESS)
     *
     * @param replayJobId the UUID of the replay job
     * @return list of all successful messages
     */
    default List<ReplayMessage> findSuccessfulMessages(UUID replayJobId) {
        return findByReplayJobIdAndStatus(replayJobId, ReplayMessageStatus.SUCCESS);
    }

    /**
     * Delete all messages for a replay job
     * SQL: DELETE FROM replay_messages WHERE replay_job_id = ?
     * Useful for: Cleanup when deleting a replay job
     *
     * @param replayJobId the UUID of the replay job
     */
    void deleteByReplayJobId(UUID replayJobId);
}
