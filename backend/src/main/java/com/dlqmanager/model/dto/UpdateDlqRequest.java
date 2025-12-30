package com.dlqmanager.model.dto;

import com.dlqmanager.model.enums.DlqStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating an existing DLQ topic
 * Used in PUT /api/dlq-topics/{id} request body
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDlqRequest {

    /**
     * Updated source topic (optional)
     */
    private String sourceTopic;

    /**
     * Updated error field path (optional)
     */
    private String errorFieldPath;

    /**
     * Updated status: ACTIVE or PAUSED (optional)
     */
    private DlqStatus status;
}
