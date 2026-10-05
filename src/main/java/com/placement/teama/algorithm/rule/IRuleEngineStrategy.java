package com.placement.teama.algorithm.rule;

import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import java.util.Map;

public interface IRuleEngineStrategy {
    EligibilityDecisionResponse evaluate(RuleSet ruleSet, StudentSnapshot student, Map<String, Object> params);
    String getStrategyName();
}