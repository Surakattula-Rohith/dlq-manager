package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a registered Dead Letter Queue topic
 * Maps to the 'dlq_topics' table in PostgreSQL
 */
@Entity
@Table(name = "dlq_topics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlqTopic {

    /**
     * Primary key - auto-generated UUID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Name of the DLQ topic in Kafka (e.g., "orders-dlq")
     * Must be unique - can't register same DLQ twice
     */
    @Column(name = "dlq_topic_name", nullable = false, unique = true)
    private String dlqTopicName;

    /**
     * Name of the source topic where messages should be replayed
     * (e.g., "orders" for "orders-dlq")
     */
    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;

    /**
     * How this DLQ was discovered: AUTO (by naming convention) or MANUAL (user registered)
     * Stored as string in database: "AUTO" or "MANUAL"
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "detection_type", nullable = false)
    private DetectionType detectionType;

    /**
     * Optional: JSON path to extract error message from Kafka message
     * Example: "headers.error-message" or "payload.error.reason"
     * If null, we'll try to find error info automatically
     */
    @Column(name = "error_field_path")
    private String errorFieldPath;

    /**
     * Status of this DLQ: ACTIVE (monitored) or PAUSED (ignored)
     * Stored as string in database: "ACTIVE" or "PAUSED"
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DlqStatus status = DlqStatus.ACTIVE; // Default to ACTIVE

    /**
     * When this DLQ was registered in our system
     * Automatically set by Hibernate when entity is first saved
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * When this DLQ record was last modified
     * Automatically updated by Hibernate on every save
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
