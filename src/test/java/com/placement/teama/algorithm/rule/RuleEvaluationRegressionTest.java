package com.placement.teama.algorithm.rule;

import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import com.placement.teama.model.enums.EligibilityResult;
import com.placement.teama.service.EligibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluationRegressionTest {
    private static final List<String> STRATEGIES = List.of("sequential_and", "weighted_priority", "decision_tree");

    @Test
    void lowCgpaAndExcessBacklogsBothFailAndProduceNotEligibleForEveryStrategy() {
        EligibilityService service = new EligibilityService();
        RuleSet rules = rules(
                new EligibilityRule("R-CGPA", "min_cgpa", 7.5, 1.0),
                new EligibilityRule("R-BACKLOGS", "max_backlogs", 0, 1.0));
        StudentSnapshot student = student(5.0, 3);

        for (String strategy : STRATEGIES) {
            EligibilityDecisionResponse result = service.evaluate(rules, student, strategy, Map.of());
            assertEquals(EligibilityResult.NOT_ELIGIBLE, result.getEligibilityResult(), strategy);
            assertEquals(2, result.getFailedRules().size(), strategy);
            assertTrue(result.getFailedRules().get(0).contains("CGPA 5.0"), strategy);
            assertTrue(result.getFailedRules().get(0).contains("7.5"), strategy);
            assertTrue(result.getFailedRules().get(1).contains("Backlogs 3"), strategy);
            assertTrue(result.getFailedRules().get(1).contains("0"), strategy);
        }
    }

    @Test
    void comparisonDirectionsRejectLowCgpaAndExcessBacklogs() {
        List<String> failures = new ArrayList<>();
        assertFalse(RuleHelper.checkRule(new EligibilityRule("R-CGPA", "min_cgpa", 7.5, 1.0),
                student(5.0, 0), failures));
        assertTrue(failures.get(0).contains("CGPA 5.0"));

        failures.clear();
        assertFalse(RuleHelper.checkRule(new EligibilityRule("R-BACKLOGS", "max_backlogs", 0, 1.0),
                student(8.0, 3), failures));
        assertTrue(failures.get(0).contains("Backlogs 3"));
    }

    @Test
    void satisfyingAllHardRulesRemainsEligibleAcrossStrategies() {
        EligibilityService service = new EligibilityService();
        RuleSet rules = rules(
                new EligibilityRule("R-CGPA", "min_cgpa", 7.5, 1.0),
                new EligibilityRule("R-BACKLOGS", "max_backlogs", 0, 1.0));
        StudentSnapshot student = student(8.0, 0);

        for (String strategy : STRATEGIES) {
            assertEquals(EligibilityResult.ELIGIBLE,
                    service.evaluate(rules, student, strategy, Map.of()).getEligibilityResult(), strategy);
        }
    }

    @Test
    void weightedStrategyCannotOutvoteAFailedHardRuleWithWeights() {
        EligibilityService service = new EligibilityService();
        RuleSet rules = rules(
                new EligibilityRule("R-CGPA", "min_cgpa", 7.5, 0.01),
                new EligibilityRule("R-BACKLOGS", "max_backlogs", 0, 100.0));

        EligibilityDecisionResponse result = service.evaluate(rules, student(5.0, 0), "weighted_priority", Map.of());

        assertEquals(EligibilityResult.NOT_ELIGIBLE, result.getEligibilityResult());
        assertEquals(1, result.getFailedRules().size());
        assertTrue(result.getFailedRules().get(0).contains("CGPA 5.0"));
    }

    @Test
    void missingUnsupportedAndMalformedRuleDefinitionsAreRejectedForEveryStrategy() {
        EligibilityService service = new EligibilityService();
        StudentSnapshot student = student(5.0, 3);
        List<RuleSet> invalidRules = List.of(
                rules(new EligibilityRule("R1", null, 7.5, 1.0)),
                rules(new EligibilityRule("R1", "invented_rule", 7.5, 1.0)),
                rules(new EligibilityRule("R1", "min_cgpa", null, 1.0)),
                rules(new EligibilityRule("R1", "min_cgpa", "7.5", 1.0)),
                rules(new EligibilityRule("R1", "max_backlogs", -1, 1.0)),
                rules(new EligibilityRule("R1", "max_backlogs", 0.5, 1.0))
        );

        for (String strategy : STRATEGIES) {
            for (RuleSet invalid : invalidRules) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> service.evaluate(invalid, student, strategy, Map.of()), strategy);
                assertFalse(error.getMessage().isBlank());
            }
        }
    }

    @Test
    void missingStudentBacklogsCannotDefaultToZeroAndPass() {
        StudentSnapshot missingBacklogs = StudentSnapshot.builder().studentId("STU-1").cgpa(8.0)
                .branch("CSE").attendancePct(90.0).skills(List.of("Java")).build();
        RuleSet rules = rules(new EligibilityRule("R-BACKLOGS", "max_backlogs", 0, 1.0));

        assertThrows(IllegalArgumentException.class,
                () -> new EligibilityService().evaluate(rules, missingBacklogs, "sequential_and", Map.of()));
    }

    @Test
    void documentedOmittedWeightDefaultsToOneAndIsNotTreatedAsZero() throws Exception {
        EligibilityRule rule = new ObjectMapper().readValue(
                "{\"rule_id\":\"R1\",\"rule_type\":\"min_cgpa\",\"threshold\":7.5}",
                EligibilityRule.class);

        assertEquals(1.0, rule.getWeight());
        assertEquals(EligibilityResult.NOT_ELIGIBLE,
                new EligibilityService().evaluate(rules(rule), student(5.0, 0), "weighted_priority", Map.of())
                        .getEligibilityResult());
    }

    private RuleSet rules(EligibilityRule... rules) {
        return RuleSet.builder().version("v1").rules(List.of(rules)).build();
    }

    private StudentSnapshot student(double cgpa, int backlogs) {
        return StudentSnapshot.builder().studentId("STU-TEST-102").cgpa(cgpa).backlogs(backlogs)
                .attendancePct(60.0).branch("CSE").skills(List.of("Java")).build();
    }
}
