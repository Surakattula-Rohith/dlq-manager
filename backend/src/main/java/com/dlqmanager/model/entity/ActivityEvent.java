package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.ActivityAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Activity Event
 *
 * One entry in the activity log: who did what, when, and to which thing.
 * Written once and never changed, so the log can be trusted after an incident.
 *
 * target and details are plain text on purpose: the log must still make sense
 * after the topic, rule or channel it mentions has been deleted.
 */
@Entity
@Table(
        name = "activity_events",
        indexes = {
                @Index(name = "idx_activity_events_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_activity_events_username", columnList = "username")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private ActivityAction action;

    /**
     * What it was done to, e.g. a DLQ topic or alert rule name
     */
    @Column(name = "target")
    private String target;

    /**
     * Extra context, e.g. "3 messages: 3 succeeded" or "localhost:9092 -> kafka:29092"
     */
    @Column(name = "details", length = 1000)
    private String details;
}
