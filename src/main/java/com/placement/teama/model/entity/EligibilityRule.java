package com.placement.teama.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EligibilityRule {
    @JsonAlias("ruleId")
    private String ruleId;
    @JsonAlias("ruleType")
    private String ruleType; // min_cgpa, max_backlogs, allowed_branches, min_attendance, required_skillsp
    private Object threshold;
    /** Explicit snake_case API representation for branch allow-lists. */
    private List<String> allowedValues;
    @Builder.Default
    private double weight = 1.0;

    public EligibilityRule(String ruleId, String ruleType, Object threshold,
                           List<String> allowedValues, double weight) {
        this.ruleId = ruleId;
        this.ruleType = ruleType;
        this.threshold = threshold;
        this.allowedValues = allowedValues;
        this.weight = weight;
    }

    /** Keeps the existing Java construction contract used by rules with a generic threshold. */
    public EligibilityRule(String ruleId, String ruleType, Object threshold, double weight) {
        this(ruleId, ruleType, threshold, null, weight);
    }
}
