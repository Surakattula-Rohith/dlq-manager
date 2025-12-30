package com.dlqmanager.service;

import com.dlqmanager.model.dto.RegisterDlqRequest;
import com.dlqmanager.model.dto.UpdateDlqRequest;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing DLQ topic registrations
 * Handles CRUD operations for DLQ topics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DlqDiscoveryService {

    private final DlqTopicRepository dlqTopicRepository;
    private final KafkaAdminService kafkaAdminService;

    /**
     * Register a new DLQ topic
     *
     * @param request The registration request
     * @return The registered DLQ topic entity
     * @throws IllegalArgumentException if topic already registered or doesn't exist in Kafka
     */
    @Transactional
    public DlqTopic registerDlqTopic(RegisterDlqRequest request) {
        log.info("Registering new DLQ: {} -> {}", request.getDlqTopicName(), request.getSourceTopic());

        // Check if DLQ is already registered
        Optional<DlqTopic> existing = dlqTopicRepository.findByDlqTopicName(request.getDlqTopicName());
        if (existing.isPresent()) {
            log.warn("DLQ topic already registered: {}", request.getDlqTopicName());
            throw new IllegalArgumentException("DLQ topic '" + request.getDlqTopicName() + "' is already registered");
        }

        // Verify DLQ topic exists in Kafka
        if (!kafkaAdminService.topicExists(request.getDlqTopicName())) {
            log.warn("DLQ topic does not exist in Kafka: {}", request.getDlqTopicName());
            throw new IllegalArgumentException("DLQ topic '" + request.getDlqTopicName() + "' does not exist in Kafka cluster");
        }

        // Verify source topic exists in Kafka
        if (!kafkaAdminService.topicExists(request.getSourceTopic())) {
            log.warn("Source topic does not exist in Kafka: {}", request.getSourceTopic());
            throw new IllegalArgumentException("Source topic '" + request.getSourceTopic() + "' does not exist in Kafka cluster");
        }

        // Create and save DLQ entity
        DlqTopic dlqTopic = new DlqTopic();
        dlqTopic.setDlqTopicName(request.getDlqTopicName());
        dlqTopic.setSourceTopic(request.getSourceTopic());
        dlqTopic.setDetectionType(request.getDetectionType());
        dlqTopic.setErrorFieldPath(request.getErrorFieldPath());
        dlqTopic.setStatus(DlqStatus.ACTIVE);

        DlqTopic saved = dlqTopicRepository.save(dlqTopic);
        log.info("Successfully registered DLQ: {} (ID: {})", saved.getDlqTopicName(), saved.getId());

        return saved;
    }

    /**
     * Get all registered DLQ topics
     *
     * @return List of all DLQ topics
     */
    public List<DlqTopic> getAllDlqTopics() {
        log.info("Fetching all registered DLQ topics");
        List<DlqTopic> topics = dlqTopicRepository.findAll();
        log.info("Found {} registered DLQ topics", topics.size());
        return topics;
    }

    /**
     * Get a specific DLQ topic by ID
     *
     * @param id The UUID of the DLQ topic
     * @return The DLQ topic if found
     * @throws IllegalArgumentException if DLQ topic not found
     */
    public DlqTopic getDlqTopicById(UUID id) {
        log.info("Fetching DLQ topic by ID: {}", id);

        return dlqTopicRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("DLQ topic not found: {}", id);
                return new IllegalArgumentException("DLQ topic not found with ID: " + id);
            });
    }

    /**
     * Update an existing DLQ topic
     *
     * @param id The UUID of the DLQ topic to update
     * @param request The update request
     * @return The updated DLQ topic
     * @throws IllegalArgumentException if DLQ topic not found
     */
    @Transactional
    public DlqTopic updateDlqTopic(UUID id, UpdateDlqRequest request) {
        log.info("Updating DLQ topic: {}", id);

        DlqTopic dlqTopic = getDlqTopicById(id);

        // Update fields if provided
        if (request.getSourceTopic() != null && !request.getSourceTopic().isBlank()) {
            // Verify new source topic exists in Kafka
            if (!kafkaAdminService.topicExists(request.getSourceTopic())) {
                throw new IllegalArgumentException("Source topic '" + request.getSourceTopic() + "' does not exist in Kafka cluster");
            }
            dlqTopic.setSourceTopic(request.getSourceTopic());
        }

        if (request.getErrorFieldPath() != null) {
            dlqTopic.setErrorFieldPath(request.getErrorFieldPath());
        }

        if (request.getStatus() != null) {
            dlqTopic.setStatus(request.getStatus());
        }

        DlqTopic updated = dlqTopicRepository.save(dlqTopic);
        log.info("Successfully updated DLQ topic: {}", id);

        return updated;
    }

    /**
     * Delete a DLQ topic registration
     *
     * @param id The UUID of the DLQ topic to delete
     * @throws IllegalArgumentException if DLQ topic not found
     */
    @Transactional
    public void deleteDlqTopic(UUID id) {
        log.info("Deleting DLQ topic: {}", id);

        DlqTopic dlqTopic = getDlqTopicById(id);
        dlqTopicRepository.delete(dlqTopic);

        log.info("Successfully deleted DLQ topic: {} ({})", dlqTopic.getDlqTopicName(), id);
    }

    /**
     * Get only active DLQ topics (status = ACTIVE)
     *
     * @return List of active DLQ topics
     */
    public List<DlqTopic> getActiveDlqTopics() {
        log.info("Fetching active DLQ topics");
        List<DlqTopic> topics = dlqTopicRepository.findByStatus(DlqStatus.ACTIVE);
        log.info("Found {} active DLQ topics", topics.size());
        return topics;
    }
}
