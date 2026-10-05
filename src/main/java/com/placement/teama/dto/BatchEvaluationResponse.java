package com.placement.teama.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BatchEvaluationResponse {
    private String batchId;
    private String status;
    private int totalRequests;
    private String queueStrategy;
    private Map<String, Object> summary;
    private Map<String, Object> metrics;
    private List<StudentResult> students;

    @Data @Builder
    public static class StudentResult {
        private String studentId;
        private String requestId;
        private String decisionId;
        private String state;
        private String result;
        private String slotId;
        private String leaseId;
        private String errorMessage;
    }
}
