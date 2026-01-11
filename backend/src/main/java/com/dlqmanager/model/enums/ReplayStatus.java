package com.dlqmanager.model.enums;

/**
 * Status of a replay job
 *
 * Lifecycle flow:
 * PENDING → RUNNING → COMPLETED (success)
 *        └─────────→ FAILED (error)
 */
public enum ReplayStatus {
    /**
     * Replay job created but not started yet
     * Initial state when job record is created
     */
    PENDING,

    /**
     * Replay job is currently processing messages
     * Active state - sending messages to Kafka
     */
    RUNNING,

    /**
     * Replay job finished processing all messages
     * Check succeeded/failed counts to see results
     * Note: Job can be COMPLETED even if some messages failed
     */
    COMPLETED,

    /**
     * Replay job failed to execute due to system error
     * Different from message failures - this is a job-level failure
     * Example: Kafka broker unreachable, database error, etc.
     */
    FAILED
}
