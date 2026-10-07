package com.placement.teama.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EligibilityRule {
    @JsonAlias("ruleId")
    private String ruleId;
    @JsonAlias("ruleType")
    private String ruleType; // min_cgpa, max_backlogs, allowed_branches, min_attendance, required_skillsp
    private Object threshold;
    @Builder.Default
    private double weight = 1.0;
}
