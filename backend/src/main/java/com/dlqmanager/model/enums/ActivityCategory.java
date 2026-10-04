package com.dlqmanager.model.enums;

/**
 * Groups of activity a Slack channel can follow (the "team feed")
 *
 * REPLAYS - someone replayed messages
 * ALERTS  - someone acknowledged or snoozed an alert
 * CHANGES - someone changed the setup: DLQ topics, alert rules, Slack channels, Kafka connection
 *
 * Sign-ins belong to no category: they stay on the Activity page and are never posted.
 */
public enum ActivityCategory {
    REPLAYS,
    ALERTS,
    CHANGES
}
