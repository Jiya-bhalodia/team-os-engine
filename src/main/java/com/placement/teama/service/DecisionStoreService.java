package com.placement.teama.service;

import com.placement.teama.dto.EligibilityDecisionResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory decision repository for the academic demonstrator. */
@Service
public class DecisionStoreService {
    private final Map<String, EligibilityDecisionResponse> decisions = new ConcurrentHashMap<>();

    public void save(EligibilityDecisionResponse decision) {
        decisions.put(decision.getDecisionId(), decision);
    }

    public EligibilityDecisionResponse find(String decisionId) {
        return decisions.get(decisionId);
    }
}
