package com.dlqmanager.model.dto;

import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for DLQ topic response
 * Used when returning DLQ information to the client
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlqTopicResponse {

    private UUID id;
    private String dlqTopicName;
    private String sourceTopic;
    private DetectionType detectionType;
    private String errorFieldPath;
    private DlqStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert DlqTopic entity to DlqTopicResponse DTO
     */
    public static DlqTopicResponse fromEntity(DlqTopic entity) {
        return new DlqTopicResponse(
            entity.getId(),
            entity.getDlqTopicName(),
            entity.getSourceTopic(),
            entity.getDetectionType(),
            entity.getErrorFieldPath(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
