package com.dlqmanager.model.dto;

import com.dlqmanager.model.enums.DetectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for registering a new DLQ topic
 * Used in POST /api/dlq-topics request body
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDlqRequest {

    /**
     * Name of the DLQ topic in Kafka (e.g., "orders-dlq")
     */
    @NotBlank(message = "DLQ topic name is required")
    private String dlqTopicName;

    /**
     * Name of the source topic where messages should be replayed (e.g., "orders")
     */
    @NotBlank(message = "Source topic name is required")
    private String sourceTopic;

    /**
     * How this DLQ was discovered: AUTO or MANUAL
     */
    @NotNull(message = "Detection type is required")
    private DetectionType detectionType;

    /**
     * Optional: JSON path to extract error message from Kafka message
     * Example: "headers.error-message" or "payload.error.reason"
     */
    private String errorFieldPath;
}
