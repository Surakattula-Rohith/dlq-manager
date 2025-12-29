package com.dlqmanager.model.enums;

/**
 * How the DLQ topic was discovered and registered in the system
 */
public enum DetectionType {
    /**
     * Automatically discovered by naming convention
     * (e.g., topic ending with "-dlq", "-dead-letter", "-error")
     */
    AUTO,

    /**
     * Manually registered by user through UI/API
     */
    MANUAL
}
