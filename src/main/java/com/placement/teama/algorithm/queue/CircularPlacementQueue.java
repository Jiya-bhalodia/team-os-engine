package com.placement.teama.algorithm.queue;

import com.placement.teama.model.entity.EligibilityRequestEntity;

/**
 * OS Concept: Ring Buffer / Bounded Producer-Consumer Circular Queue.
 */
public class CircularPlacementQueue implements IPlacementQueue {
    private final EligibilityRequestEntity[] buffer;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int count = 0;

    public CircularPlacementQueue(int capacity) {
        this.capacity = capacity;
        this.buffer = new EligibilityRequestEntity[capacity];
    }

    @Override
    public synchronized boolean enqueue(EligibilityRequestEntity request) {
        if (count == capacity) return false; // Overflow safety
        buffer[tail] = request;
        tail = (tail + 1) % capacity;
        count++;
        return true;
    }

    @Override
    public synchronized EligibilityRequestEntity dequeue() {
        if (count == 0) return null;
        EligibilityRequestEntity item = buffer[head];
        buffer[head] = null;
        head = (head + 1) % capacity;
        count--;
        return item;
    }

    @Override
    public synchronized int getPosition(String requestId) {
        int pos = 1;
        int curr = head;
        for (int i = 0; i < count; i++) {
            if (buffer[curr] != null && buffer[curr].getRequestId().equals(requestId)) {
                return pos;
            }
            curr = (curr + 1) % capacity;
            pos++;
        }
        return -1;
    }

    @Override
    public synchronized int size() { return count; }

    @Override
    public synchronized boolean isEmpty() { return count == 0; }

    @Override
    public String getStrategyName() { return "CIRCULAR"; }
}