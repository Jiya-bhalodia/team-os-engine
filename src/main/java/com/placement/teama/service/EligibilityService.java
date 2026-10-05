package com.placement.teama.service;

import com.placement.teama.algorithm.rule.*;
import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class EligibilityService {

    private final Map<String, IRuleEngineStrategy> strategies = new HashMap<>();

    public EligibilityService() {
        registerStrategy(new SequentialAndStrategy());
        registerStrategy(new WeightedPriorityStrategy());
        registerStrategy(new DecisionTreeStrategy());
    }

    private void registerStrategy(IRuleEngineStrategy strategy) {
        strategies.put(strategy.getStrategyName().toLowerCase(), strategy);
    }

    public EligibilityDecisionResponse evaluate(
            RuleSet ruleSet,
            StudentSnapshot student,
            String strategyName,
            Map<String, Object> params) {

        if (ruleSet == null) {
            throw new IllegalArgumentException("Rule set is required");
        }

        if (student == null) {
            throw new IllegalArgumentException("Student snapshot is required");
        }

        if (strategyName == null || strategyName.isBlank()) {
            strategyName = "sequential_and";
        }

        IRuleEngineStrategy strategy =
                strategies.get(strategyName.toLowerCase());

        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unsupported rule strategy: " + strategyName);
        }

        return strategy.evaluate(ruleSet, student, params);
    }

    public boolean supportsStrategy(String strategyName) {
        return strategyName != null && !strategyName.isBlank()
                && strategies.containsKey(strategyName.toLowerCase());
    }
}