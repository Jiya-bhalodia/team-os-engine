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

public class WeightedPriorityStrategy implements IRuleEngineStrategy {
    @Override
    public EligibilityDecisionResponse evaluate(RuleSet ruleSet, StudentSnapshot student, Map<String, Object> params) {
        long start = System.nanoTime();
        List<String> failedRules = new ArrayList<>();
        double totalWeight = 0.0;
        double passedWeight = 0.0;

        for (EligibilityRule rule : ruleSet.getRules()) {
            totalWeight += rule.getWeight();
            boolean ok = RuleHelper.checkRule(rule, student, failedRules);
            if (ok) passedWeight += rule.getWeight();
        }

        double scorePct = totalWeight > 0 ? (passedWeight / totalWeight) * 100.0 : 0.0;
        EligibilityResult finalResult;

        if (scorePct >= 80.0) {
            finalResult = EligibilityResult.ELIGIBLE;
        } else if (scorePct >= 50.0) {
            finalResult = EligibilityResult.CONDITIONAL;
        } else {
            finalResult = EligibilityResult.NOT_ELIGIBLE;
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        return EligibilityDecisionResponse.builder()
                .decisionId("DEC-" + UUID.randomUUID().toString().substring(0, 8))
                .eligibilityResult(finalResult)
                .failedRules(failedRules)
                .ruleSetVersion(ruleSet.getVersion())
                .decisionMetrics(new EligibilityDecisionResponse.DecisionMetrics(elapsedMs, ruleSet.getRules().size()))
                .build();
    }

    @Override
    public String getStrategyName() { return "weighted_priority"; }
}