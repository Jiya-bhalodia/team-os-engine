package com.placement.teama.algorithm.queue;

import com.placement.teama.model.entity.EligibilityRequestEntity;
import java.util.PriorityQueue;

/**
 * OS Concept: Binary Max-Heap Priority Queue for O(log N) fast insertions & extractions.
 */
public class HeapPlacementQueue implements IPlacementQueue {
    private final PriorityQueue<EligibilityRequestEntity> heap = new PriorityQueue<>();

    @Override
    public synchronized boolean enqueue(EligibilityRequestEntity request) {
        return heap.offer(request);
    }

    @Override
    public synchronized EligibilityRequestEntity dequeue() {
        return heap.poll();
    }

    @Override
    public synchronized int getPosition(String requestId) {
        Object[] array = heap.stream().sorted().toArray();
        for (int i = 0; i < array.length; i++) {
            if (((EligibilityRequestEntity) array[i]).getRequestId().equals(requestId)) {
                return i + 1;
            }
        }
        return -1;
    }

    @Override
    public synchronized int size() { return heap.size(); }

    @Override
    public synchronized boolean isEmpty() { return heap.isEmpty(); }

    @Override
    public String getStrategyName() { return "HEAP"; }
}