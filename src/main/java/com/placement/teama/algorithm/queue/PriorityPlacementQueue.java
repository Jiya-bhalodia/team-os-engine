package com.placement.teama.algorithm.queue;

import com.placement.teama.model.entity.EligibilityRequestEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OS Concept: Sorted List Priority CPU Scheduler (O(N) Insertion, O(1) Pop).
 */
public class PriorityPlacementQueue implements IPlacementQueue {
    private final List<EligibilityRequestEntity> list = Collections.synchronizedList(new ArrayList<>());

    @Override
    public boolean enqueue(EligibilityRequestEntity request) {
        synchronized (list) {
            list.add(request);
            list.sort(null);
            return true;
        }
    }

    @Override
    public EligibilityRequestEntity dequeue() {
        synchronized (list) {
            if (list.isEmpty()) return null;
            return list.remove(0);
        }
    }

    @Override
    public int getPosition(String requestId) {
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getRequestId().equals(requestId)) return i + 1;
            }
            return -1;
        }
    }

    @Override
    public int size() { return list.size(); }

    @Override
    public boolean isEmpty() { return list.isEmpty(); }

    @Override
    public String getStrategyName() { return "PRIORITY"; }
}