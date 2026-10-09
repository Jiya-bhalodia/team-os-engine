package com.placement.teama.service;

import com.placement.teama.dto.EligibilityDecisionResponse;

public interface TeamCDecisionCallback {
    void deliver(String applicationId, EligibilityDecisionResponse decision, String correlationId);
}
