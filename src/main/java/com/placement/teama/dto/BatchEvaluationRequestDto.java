package com.placement.teama.dto;

import com.placement.teama.model.entity.RuleSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BatchEvaluationRequestDto {
    @NotBlank private String driveId;
    @NotBlank private String ruleSetVersion;
    @NotNull @Valid private RuleSet ruleSet;
    private String chainingStrategy = "sequential_and";
    private Map<String, Object> policyParameters;
    @NotEmpty @Valid private List<BatchStudentRequestDto> students;
}
