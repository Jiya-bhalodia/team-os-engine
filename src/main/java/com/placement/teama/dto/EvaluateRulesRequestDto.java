package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvaluateRulesRequestDto {
    @JsonAlias("ruleSet")
    @NotNull private RuleSet ruleSet;
    @NotNull private StudentSnapshot student;
    @JsonAlias("chainingStrategy")
    private String chainingStrategy = "sequential_and";
    @JsonAlias("policyParameters")
    private Map<String, Object> policyParameters;
}
