package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_rule_id", nullable = false)
    private AlertRule alertRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AlertStatus status = AlertStatus.FIRING;

    @Column(name = "message_count", nullable = false)
    private Long messageCount;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    // Username of whoever acknowledged the alert
    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    /** When a snoozed alert should resume firing. */
    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

    // Username of whoever snoozed the alert (kept after the snooze ends)
    @Column(name = "snoozed_by")
    private String snoozedBy;

    /**
     * When the problem went away (the rule's condition stopped holding). Null while the
     * alert is open. A rule has at most one open alert; a new one is raised only after
     * this one is resolved.
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
