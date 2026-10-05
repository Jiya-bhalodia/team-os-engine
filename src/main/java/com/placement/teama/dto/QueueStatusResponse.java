package com.placement.teama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class QueueStatusResponse {
    private String applicationId;
    private String requestId;
    private int queuePosition;
    private String state;
    private double estimatedEvaluationTimeMs;
    private String acceptedPolicy;
    private String correlationId;
    private String studentId;
    private String driveId;
    private String decisionId;
    private String eligibilityResult;
    private String slotId;
    private String leaseId;
    private String errorMessage;
    private java.util.List<String> failedRules;
    private String ruleSetVersion;
    private java.time.Instant submittedAt;
    private java.time.Instant processingStartedAt;
    private java.time.Instant completedAt;
}
