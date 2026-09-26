package com.dlqmanager.repository;

import com.dlqmanager.model.entity.ReplayMessage;
import com.dlqmanager.model.enums.ReplayMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Find replay results for every job of a DLQ topic, filtered by status
     * Useful for: Knowing which DLQ messages have already been replayed
     *
     * @param dlqTopicId the UUID of the DLQ topic
     * @param status the status to filter by
     * @return list of replay results across all jobs for that topic
     */
    @Query("SELECT rm FROM ReplayMessage rm WHERE rm.replayJob.dlqTopic.id = :dlqTopicId AND rm.status = :status")
    List<ReplayMessage> findReplaysForDlqTopic(@Param("dlqTopicId") UUID dlqTopicId,
                                               @Param("status") ReplayMessageStatus status);

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
     * Which DLQ messages have already been replayed successfully?
     *
     * Key: "partition:offset" (see offsetKey)
     * Value: time of the most recent successful replay
     *
     * @param dlqTopicId the UUID of the DLQ topic
     * @return map of replayed message positions
     */
    default Map<String, LocalDateTime> findReplayedOffsets(UUID dlqTopicId) {
        Map<String, LocalDateTime> replayed = new HashMap<>();
        for (ReplayMessage rm : findReplaysForDlqTopic(dlqTopicId, ReplayMessageStatus.SUCCESS)) {
            String key = offsetKey(rm.getDlqPartition(), rm.getDlqOffset());
            LocalDateTime previous = replayed.get(key);
            LocalDateTime current = rm.getReplayedAt();
            if (previous == null || (current != null && current.isAfter(previous))) {
                replayed.put(key, current);
            }
        }
        return replayed;
    }

    /**
     * Build the key used to identify a message inside a DLQ topic
     */
    static String offsetKey(Integer partition, Long offset) {
        return partition + ":" + offset;
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
