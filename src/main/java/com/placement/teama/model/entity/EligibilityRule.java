package com.placement.teama.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityRule {
    private String ruleId;
    private String ruleType; // min_cgpa, max_backlogs, allowed_branches, min_attendance, required_skillsp
    private Object threshold;
    @Builder.Default
    private double weight = 1.0;
}