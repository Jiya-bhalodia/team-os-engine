package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.placement.teama.model.entity.RuleSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BatchEvaluationRequestDto {
    @JsonAlias("driveId")
    @NotBlank private String driveId;
    @JsonAlias("ruleSetVersion")
    @NotBlank private String ruleSetVersion;
    @JsonAlias("ruleSet")
    @NotNull @Valid private RuleSet ruleSet;
    @JsonAlias("chainingStrategy")
    private String chainingStrategy = "sequential_and";
    @JsonAlias("policyParameters")
    private Map<String, Object> policyParameters;
    @NotEmpty @Valid private List<BatchStudentRequestDto> students;
}
