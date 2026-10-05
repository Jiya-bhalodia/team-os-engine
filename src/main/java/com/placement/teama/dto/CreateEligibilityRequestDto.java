package com.placement.teama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;

import java.util.Map;

@Data
public class CreateEligibilityRequestDto {
    @NotBlank(message = "applicationId is required")
    private String applicationId;
    @NotBlank(message = "studentId is required")
    private String studentId;
    @NotBlank(message = "drive_id is required")
    private String driveId;
    @NotBlank(message = "ruleSetVersion is required")
    private String ruleSetVersion;
    private int priority = 1;
    /** The caller (normally Team C) supplies the snapshot and versioned drive rules. */
    @NotNull(message = "student is required")
    private StudentSnapshot student;
    @NotNull(message = "ruleSet is required")
    private RuleSet ruleSet;
    private String chainingStrategy = "sequential_and";
    private Map<String, Object> policyParameters;
    /** Optional: when supplied, an eligible request attempts an exclusive slot lease. */
    private String slotId;
    private Integer slotLeaseTtlSeconds = 300;
}
