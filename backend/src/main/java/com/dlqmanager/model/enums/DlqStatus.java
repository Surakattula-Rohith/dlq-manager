package com.dlqmanager.model.enums;

/**
 * Status of a DLQ topic in the system
 */
public enum DlqStatus {
    /**
     * DLQ is actively monitored and available for replay
     */
    ACTIVE,

    /**
     * DLQ monitoring is paused (still exists in Kafka, but not tracked)
     */
    PAUSED
}
