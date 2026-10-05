package com.placement.teama.algorithm.queue;

import com.placement.teama.model.entity.EligibilityRequestEntity;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * OS Concept: First-Come, First-Served (FCFS) CPU Scheduling Queue.
 */
public class FifoPlacementQueue implements IPlacementQueue {
    private final ConcurrentLinkedQueue<EligibilityRequestEntity> queue = new ConcurrentLinkedQueue<>();

    @Override
    public boolean enqueue(EligibilityRequestEntity request) {
        return queue.add(request);
    }

    @Override
    public EligibilityRequestEntity dequeue() {
        return queue.poll();
    }

    @Override
    public int getPosition(String requestId) {
        int pos = 1;
        for (EligibilityRequestEntity req : queue) {
            if (req.getRequestId().equals(requestId)) return pos;
            pos++;
        }
        return -1;
    }

    @Override
    public int size() { return queue.size(); }

    @Override
    public boolean isEmpty() { return queue.isEmpty(); }

    @Override
    public String getStrategyName() { return "FIFO"; }
}