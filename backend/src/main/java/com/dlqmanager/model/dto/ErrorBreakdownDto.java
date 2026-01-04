package com.dlqmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error Breakdown DTO
 *
 * Purpose: Represents statistics for a single error type in a DLQ topic
 *
 * Used for the error breakdown endpoint to show:
 * - What types of errors are occurring
 * - How many messages have each error
 * - What percentage each error represents
 *
 * Example:
 * {
 *   "errorType": "DB Connection Timeout",
 *   "count": 35,
 *   "percentage": 68.6
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorBreakdownDto {

    /**
     * The error type extracted from message headers
     * Example: "DB Connection Timeout", "Invalid JSON Format"
     */
    private String errorType;

    /**
     * Number of messages with this error type
     */
    private Long count;

    /**
     * Percentage of total messages (0-100)
     * Calculated as: (count / totalMessages) * 100
     */
    private Double percentage;
}
