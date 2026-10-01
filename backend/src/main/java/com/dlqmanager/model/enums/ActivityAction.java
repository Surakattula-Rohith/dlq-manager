package com.dlqmanager.model.enums;

/**
 * Things people do in DLQ Manager that end up in the activity log
 */
public enum ActivityAction {
    SIGNED_IN,
    SIGNED_OUT,
    SIGN_IN_FAILED,

    MESSAGES_REPLAYED,

    ALERT_ACKNOWLEDGED,
    ALERT_SNOOZED,

    DLQ_TOPIC_ADDED,
    DLQ_TOPIC_UPDATED,
    DLQ_TOPIC_DELETED,

    ALERT_RULE_CREATED,
    ALERT_RULE_UPDATED,
    ALERT_RULE_ENABLED,
    ALERT_RULE_DISABLED,
    ALERT_RULE_DELETED,

    CHANNEL_CREATED,
    CHANNEL_UPDATED,
    CHANNEL_DELETED,

    KAFKA_SETTINGS_CHANGED
}
