package com.placement.teama.algorithm.queue;

import com.placement.teama.model.entity.EligibilityRequestEntity;

public interface IPlacementQueue {
    boolean enqueue(EligibilityRequestEntity request);
    EligibilityRequestEntity dequeue();
    int getPosition(String requestId);
    int size();
    boolean isEmpty();
    String getStrategyName();
}