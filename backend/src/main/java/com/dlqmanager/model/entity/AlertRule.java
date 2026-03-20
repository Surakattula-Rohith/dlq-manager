package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.AlertType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dlq_topic_id", nullable = false)
    private DlqTopic dlqTopic;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    /** For THRESHOLD: fire when count >= this. For TIME_WINDOW: fire when increase >= this. */
    @Column(name = "threshold", nullable = false)
    private Long threshold;

    /** For TIME_WINDOW only: the lookback window in minutes. */
    @Column(name = "window_minutes")
    private Integer windowMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_channel_id")
    private NotificationChannel notificationChannel;

    /** Minimum minutes between consecutive firings for this rule. */
    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes = 30;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Tracks last fired time for cooldown enforcement. */
    @Column(name = "last_fired_at")
    private LocalDateTime lastFiredAt;

    /** Snapshot of message count at last evaluation (used for TIME_WINDOW). */
    @Column(name = "last_checked_count")
    private Long lastCheckedCount;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
