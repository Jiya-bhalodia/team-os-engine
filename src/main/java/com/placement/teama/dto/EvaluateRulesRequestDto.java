package com.placement.teama.dto;

import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class EvaluateRulesRequestDto {
    @NotNull private RuleSet ruleSet;
    @NotNull private StudentSnapshot student;
    private String chainingStrategy = "sequential_and";
    private Map<String, Object> policyParameters;
}