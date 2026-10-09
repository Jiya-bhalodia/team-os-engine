package com.placement.teama.algorithm.rule;

import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import com.placement.teama.model.enums.EligibilityResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DecisionTreeStrategy implements IRuleEngineStrategy {
    @Override
    public EligibilityDecisionResponse evaluate(RuleSet ruleSet, StudentSnapshot student, Map<String, Object> params) {
        RuleSetValidator.validate(ruleSet, student);
        long start = System.nanoTime();
        List<String> failed = new ArrayList<>();
        int rulesChecked = 0;

        // Node 1: Hard Check - CGPA & Backlogs
        for (EligibilityRule rule : ruleSet.getRules()) {
            if (RuleSetValidator.isHardRequirement(rule.getRuleType())) {
                rulesChecked++;
                RuleHelper.checkRule(rule, student, failed);
            }
        }
        if (!failed.isEmpty()) return buildResponse(EligibilityResult.NOT_ELIGIBLE, failed,
                ruleSet.getVersion(), start, rulesChecked);

        // Node 2: Soft Requirements - Branch / Skills -> Leads to CONDITIONAL status if missing
        boolean softFailed = false;
        for (EligibilityRule rule : ruleSet.getRules()) {
            if ("allowed_branches".equalsIgnoreCase(rule.getRuleType()) ||
                "min_attendance".equalsIgnoreCase(rule.getRuleType()) ||
                "required_skills".equalsIgnoreCase(rule.getRuleType())) {
                rulesChecked++;
                if (!RuleHelper.checkRule(rule, student, failed)) {
                    softFailed = true;
                }
            }
        }

        EligibilityResult result = softFailed ? EligibilityResult.CONDITIONAL : EligibilityResult.ELIGIBLE;
        return buildResponse(result, failed, ruleSet.getVersion(), start, rulesChecked);
    }

    private EligibilityDecisionResponse buildResponse(EligibilityResult result, List<String> failed, String version, long startNano, int checked) {
        double ms = (System.nanoTime() - startNano) / 1_000_000.0;
        return EligibilityDecisionResponse.builder()
                .decisionId("DEC-" + UUID.randomUUID().toString().substring(0, 8))
                .eligibilityResult(result)
                .failedRules(failed)
                .ruleSetVersion(version)
                .decisionMetrics(new EligibilityDecisionResponse.DecisionMetrics(ms, checked))
                .build();
    }

    @Override
    public String getStrategyName() { return "decision_tree"; }
}
