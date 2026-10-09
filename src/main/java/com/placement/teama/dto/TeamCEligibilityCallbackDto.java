package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.placement.teama.model.enums.EligibilityResult;

import java.util.List;

/** Payload delivered to Team C after Team A has completed an eligibility decision. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamCEligibilityCallbackDto(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("decision_id") String decisionId,
        @JsonProperty("result") EligibilityResult result,
        @JsonProperty("rule_set_version") String ruleSetVersion,
        @JsonProperty("failed_rules") List<String> failedRules,
        @JsonProperty("lease_id") String leaseId) {
}
