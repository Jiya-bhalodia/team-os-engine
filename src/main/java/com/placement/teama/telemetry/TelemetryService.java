package com.placement.teama.telemetry;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TelemetryService {
    private long evaluations = 0;
    private long requestsQueued = 0;
    private long requestsProcessing = 0;
    private long eligibleCount = 0;
    private long rejectedCount = 0;
    private long conditionalCount = 0;
    private long deadlocksDetected = 0;
    private long lockConflicts = 0;
    private long completedRequests = 0;
    private long failedRequests = 0;
    private long slotAllocationSuccesses = 0;
    private long slotAllocationFailures = 0;
    private long lockAttempts = 0;
    private long deadlocksResolved = 0;
    private final List<Double> waitTimes = Collections.synchronizedList(new ArrayList<>());
    private final List<Double> evalTimes = Collections.synchronizedList(new ArrayList<>());

    public synchronized void recordEvaluation(String result, double evalMs, double waitMs) {
        evaluations++;
        evalTimes.add(evalMs);
        waitTimes.add(waitMs);
        if ("ELIGIBLE".equals(result)) eligibleCount++;
        else if ("NOT_ELIGIBLE".equals(result)) rejectedCount++;
        else if ("CONDITIONAL".equals(result)) conditionalCount++;
    }

    public synchronized void recordDeadlock() { deadlocksDetected++; }
    public synchronized void recordDeadlockResolved() { deadlocksResolved++; }
    public synchronized void recordLockConflict() { lockConflicts++; }
    /** Completed includes both successful allocation and valid NOT_ELIGIBLE decisions. */
    public synchronized void recordCompleted() {
        completedRequests++;
    }
    public synchronized void recordFailure() { failedRequests++; }
    public synchronized void recordSlotAllocation(boolean successful) {
        if (successful) slotAllocationSuccesses++; else slotAllocationFailures++;
    }
    public synchronized void recordLockAttempt() { lockAttempts++; }
    public synchronized void recordQueued() { requestsQueued++; }
    public synchronized void recordProcessingStarted() { requestsProcessing++; }

    public synchronized Map<String, Object> getMetricsSummary(int currentQueueDepth) {
        double avgWait = waitTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxWait = waitTimes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double avgEval = evalTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("queue_depth", currentQueueDepth);
        metrics.put("requests_queued", requestsQueued);
        metrics.put("requests_processing_started", requestsProcessing);
        metrics.put("evaluations", evaluations);
        metrics.put("completed_requests", completedRequests);
        metrics.put("failed_requests", failedRequests);
        metrics.put("waiting_time_ms", Map.of(
                "average", Math.round(avgWait * 100.0) / 100.0,
                "maximum", Math.round(maxWait * 100.0) / 100.0
        ));
        metrics.put("average_rule_evaluation_time_ms", Math.round(avgEval * 100.0) / 100.0);
        metrics.put("decisions", Map.of(
                "eligible", eligibleCount,
                "rejected", rejectedCount,
                "conditional", conditionalCount
        ));
        metrics.put("anomalies", Map.of(
                "deadlocks_detected", deadlocksDetected,
                "deadlocks_resolved", deadlocksResolved,
                "slot_allocation_conflicts", lockConflicts
        ));
        metrics.put("slot_allocations", Map.of(
                "successful", slotAllocationSuccesses,
                "failed", slotAllocationFailures));
        metrics.put("lock_attempts", lockAttempts);
        return metrics;
    }
}
