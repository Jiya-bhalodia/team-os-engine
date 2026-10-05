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

public class SequentialAndStrategy implements IRuleEngineStrategy {
    @Override
    public EligibilityDecisionResponse evaluate(RuleSet ruleSet, StudentSnapshot student, Map<String, Object> params) {
        long start = System.nanoTime();
        List<String> failedRules = new ArrayList<>();
        boolean passedAll = true;

        for (EligibilityRule rule : ruleSet.getRules()) {
            boolean result = RuleHelper.checkRule(rule, student, failedRules);
            if (!result) passedAll = false;
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        return EligibilityDecisionResponse.builder()
                .decisionId("DEC-" + UUID.randomUUID().toString().substring(0, 8))
                .eligibilityResult(passedAll ? EligibilityResult.ELIGIBLE : EligibilityResult.NOT_ELIGIBLE)
                .failedRules(failedRules)
                .ruleSetVersion(ruleSet.getVersion())
                .decisionMetrics(new EligibilityDecisionResponse.DecisionMetrics(elapsedMs, ruleSet.getRules().size()))
                .build();
    }

    @Override
    public String getStrategyName() { return "sequential_and"; }
}