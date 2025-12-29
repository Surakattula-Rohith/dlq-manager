package com.dlqmanager.repository;

import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DlqStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for DlqTopic entity
 * Spring Data JPA automatically implements all methods at runtime
 */
@Repository
public interface DlqTopicRepository extends JpaRepository<DlqTopic, UUID> {

    /**
     * Find a DLQ by its topic name
     * SQL: SELECT * FROM dlq_topics WHERE dlq_topic_name = ?
     *
     * @param dlqTopicName the name of the DLQ topic
     * @return Optional containing the DlqTopic if found, empty otherwise
     */
    Optional<DlqTopic> findByDlqTopicName(String dlqTopicName);

    /**
     * Find all DLQs with a specific status
     * SQL: SELECT * FROM dlq_topics WHERE status = ?
     *
     * @param status the status to filter by (ACTIVE or PAUSED)
     * @return list of DlqTopics with the given status
     */
    List<DlqTopic> findByStatus(DlqStatus status);

    /**
     * Check if a DLQ with given name already exists
     * SQL: SELECT COUNT(*) FROM dlq_topics WHERE dlq_topic_name = ?
     *
     * @param dlqTopicName the name to check
     * @return true if exists, false otherwise
     */
    boolean existsByDlqTopicName(String dlqTopicName);

    /**
     * Find all active DLQs (commonly used query)
     * Convenience method - same as findByStatus(DlqStatus.ACTIVE)
     *
     * @return list of all active DLQ topics
     */
    default List<DlqTopic> findAllActive() {
        return findByStatus(DlqStatus.ACTIVE);
    }
}
