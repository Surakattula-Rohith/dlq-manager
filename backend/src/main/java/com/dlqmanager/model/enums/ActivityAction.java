package com.dlqmanager.model.enums;

/**
 * Things people do in DLQ Manager that end up in the activity log
 */
public enum ActivityAction {
    SIGNED_IN(null),
    SIGNED_OUT(null),
    SIGN_IN_FAILED(null),

    MESSAGES_REPLAYED(ActivityCategory.REPLAYS),

    ALERT_ACKNOWLEDGED(ActivityCategory.ALERTS),
    ALERT_SNOOZED(ActivityCategory.ALERTS),

    DLQ_TOPIC_ADDED(ActivityCategory.CHANGES),
    DLQ_TOPIC_UPDATED(ActivityCategory.CHANGES),
    DLQ_TOPIC_DELETED(ActivityCategory.CHANGES),

    ALERT_RULE_CREATED(ActivityCategory.CHANGES),
    ALERT_RULE_UPDATED(ActivityCategory.CHANGES),
    ALERT_RULE_ENABLED(ActivityCategory.CHANGES),
    ALERT_RULE_DISABLED(ActivityCategory.CHANGES),
    ALERT_RULE_DELETED(ActivityCategory.CHANGES),

    CHANNEL_CREATED(ActivityCategory.CHANGES),
    CHANNEL_UPDATED(ActivityCategory.CHANGES),
    CHANNEL_DELETED(ActivityCategory.CHANGES),

    KAFKA_SETTINGS_CHANGED(ActivityCategory.CHANGES);

    private final ActivityCategory category;

    ActivityAction(ActivityCategory category) {
        this.category = category;
    }

    /**
     * The team-feed category this action is posted under, or null if it is never posted to Slack
     */
    public ActivityCategory category() {
        return category;
    }
}
