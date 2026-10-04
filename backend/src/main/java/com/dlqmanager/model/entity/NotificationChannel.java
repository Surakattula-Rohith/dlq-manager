package com.dlqmanager.model.entity;

import com.dlqmanager.model.enums.ActivityCategory;
import com.dlqmanager.model.enums.NotificationChannelType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "notification_channels")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationChannelType type;

    /**
     * JSON config. Keys by type:
     * SLACK: {"webhookUrl": "https://hooks.slack.com/..."}
     */
    @Column(name = "configuration", columnDefinition = "TEXT", nullable = false)
    private String configuration;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Team feed: the kinds of activity (replays, alert actions, setup changes) that are
     * posted to this channel as they happen. Empty = the channel is only used for alerts.
     */
    @Convert(converter = ActivityCategorySetConverter.class)
    @Column(name = "activity_feed")
    private Set<ActivityCategory> activityFeed = EnumSet.noneOf(ActivityCategory.class);

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
