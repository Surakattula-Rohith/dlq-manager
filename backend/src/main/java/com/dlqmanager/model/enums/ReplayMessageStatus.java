package com.dlqmanager.model.enums;

/**
 * Status of an individual message replay attempt
 *
 * This is different from ReplayStatus:
 * - ReplayStatus: Overall job status (PENDING, RUNNING, COMPLETED, FAILED)
 * - ReplayMessageStatus: Individual message result (SUCCESS or FAILED)
 */
public enum ReplayMessageStatus {
    /**
     * Message was successfully sent to the source topic
     * Kafka producer confirmed receipt
     */
    SUCCESS,

    /**
     * Failed to send message to source topic
     * Could be due to:
     * - Kafka broker error
     * - Network timeout
     * - Serialization error
     * - Topic doesn't exist
     * Check errorMessage field for details
     */
    FAILED
}
