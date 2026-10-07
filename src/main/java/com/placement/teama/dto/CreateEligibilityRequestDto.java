package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;

import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateEligibilityRequestDto {
    @NotBlank(message = "applicationId is required")
    @JsonAlias("applicationId")
    private String applicationId;
    @NotBlank(message = "studentId is required")
    @JsonAlias("studentId")
    private String studentId;
    @NotBlank(message = "drive_id is required")
    @JsonAlias("driveId")
    private String driveId;
    @NotBlank(message = "ruleSetVersion is required")
    @JsonAlias("ruleSetVersion")
    private String ruleSetVersion;
    private int priority = 1;
    /** The caller (normally Team C) supplies the snapshot and versioned drive rules. */
    @NotNull(message = "student is required")
    private StudentSnapshot student;
    @NotNull(message = "ruleSet is required")
    @JsonAlias("ruleSet")
    private RuleSet ruleSet;
    @JsonAlias("chainingStrategy")
    private String chainingStrategy = "sequential_and";
    @JsonAlias("policyParameters")
    private Map<String, Object> policyParameters;
    /** Optional: when supplied, an eligible request attempts an exclusive slot lease. */
    @JsonAlias("slotId")
    private String slotId;
    @JsonAlias("slotLeaseTtlSeconds")
    private Integer slotLeaseTtlSeconds = 300;
}
