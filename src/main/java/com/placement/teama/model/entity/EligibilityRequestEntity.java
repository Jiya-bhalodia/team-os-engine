package com.placement.teama.model.entity;

import com.placement.teama.model.enums.RequestState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;
import com.placement.teama.model.enums.EligibilityResult;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityRequestEntity implements Comparable<EligibilityRequestEntity> {
    /** Team C owned identifier; Team A only carries it through runtime processing. */
    private String applicationId;
    private String requestId;
    private String studentId;
    private String driveId;
    private String ruleSetVersion;
    private int priority; // Higher integer = higher priority
    private Instant submittedAt;
    private RequestState state;
    private String correlationId;
    private String idempotencyKey;
    private StudentSnapshot student;
    private RuleSet ruleSet;
    private String chainingStrategy;
    private Map<String, Object> policyParameters;
    private String slotId;
    private Integer slotLeaseTtlSeconds;
    private String decisionId;
    private EligibilityResult eligibilityResult;
    private String leaseId;
    private String errorMessage;
    private java.util.List<String> failedRules;
    private Instant processingStartedAt;
    private Instant completedAt;

    @Override
    public int compareTo(EligibilityRequestEntity o) {
        // High priority first; tie-breaker: FIFO by submittedAt timestamp
        if (this.priority != o.priority) {
            return Integer.compare(o.priority, this.priority);
        }
        return this.submittedAt.compareTo(o.submittedAt);
    }
}
