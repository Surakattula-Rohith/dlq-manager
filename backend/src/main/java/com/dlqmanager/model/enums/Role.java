package com.dlqmanager.model.enums;

/**
 * Access level of a signed-in user. Each role can do everything the roles above it can.
 */
public enum Role {
    /** Browse, search and export messages; see replay history, alerts and settings */
    VIEWER,
    /** Also replay messages and acknowledge or snooze alerts */
    OPERATOR,
    /** Also manage DLQ topics, alert rules, Slack channels and Kafka settings */
    ADMIN
}
