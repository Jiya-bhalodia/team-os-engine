package com.placement.teama.dto;

import com.placement.teama.model.enums.EligibilityResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class EligibilityDecisionResponse {
    private String decisionId;
    private String requestId;
    private String applicationId;
    private String correlationId;
    private String leaseId;
    private EligibilityResult eligibilityResult;
    private List<String> failedRules;
    private String ruleSetVersion;
    private DecisionMetrics decisionMetrics;

    @Data
    @AllArgsConstructor
    public static class DecisionMetrics {
        private double evaluationTimeMs;
        private int rulesChecked;
    }
}
