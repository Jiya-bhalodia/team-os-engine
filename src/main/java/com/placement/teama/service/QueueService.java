package com.placement.teama.service;

import com.placement.teama.algorithm.queue.*;
import com.placement.teama.model.entity.EligibilityRequestEntity;
import com.placement.teama.model.enums.RequestState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueueService {

    private IPlacementQueue queue;

    private final Map<String, EligibilityRequestEntity> requestStore =
            new ConcurrentHashMap<>();

    private final Map<String, EligibilityRequestEntity> idempotencyStore =
            new ConcurrentHashMap<>();

    public QueueService(
            @Value("${teama.queue.strategy:heap}") String strategy,
            @Value("${teama.queue.circular-capacity:1000}") int capacity) {

        setStrategy(strategy, capacity);
    }

    public synchronized void setStrategy(String strategy, int capacity) {

        if (strategy == null || strategy.isBlank()) {
            throw new IllegalArgumentException("Queue strategy is required");
        }

        switch (strategy.toLowerCase()) {

            case "fifo":
                this.queue = new FifoPlacementQueue();
                break;

            case "circular":
                if (capacity <= 0) {
                    throw new IllegalArgumentException(
                            "Circular queue capacity must be greater than 0");
                }
                this.queue = new CircularPlacementQueue(capacity);
                break;

            case "priority":
                this.queue = new PriorityPlacementQueue();
                break;

            case "heap":
                this.queue = new HeapPlacementQueue();
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported queue strategy: " + strategy);
        }
    }

    public synchronized EligibilityRequestEntity enqueue(
            String applicationId,
            String studentId,
            String driveId,
            String version,
            int priority,
            String correlationId,
            String idempotencyKey,
            EligibilityRequestEntity requestDetails) {

        // Return previously created request for duplicate request
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {

            EligibilityRequestEntity existing =
                    idempotencyStore.get(idempotencyKey);

            if (existing != null) {
                if (!sameRequest(existing, applicationId, studentId, driveId, version, priority, requestDetails)) {
                    throw new IdempotencyConflictException(
                            "Idempotency-Key was already used with different request data");
                }
                return existing;
            }
        }

        EligibilityRequestEntity req =
                EligibilityRequestEntity.builder()
                        .applicationId(applicationId)
                        .requestId(
                                "REQ-" +
                                UUID.randomUUID()
                                        .toString()
                                        .substring(0, 8))
                        .studentId(studentId)
                        .driveId(driveId)
                        .ruleSetVersion(version)
                        .priority(priority)
                        .submittedAt(Instant.now())
                        .state(RequestState.QUEUED)
                        .correlationId(correlationId)
                        .idempotencyKey(idempotencyKey)
                        .student(requestDetails.getStudent())
                        .ruleSet(requestDetails.getRuleSet())
                        .chainingStrategy(requestDetails.getChainingStrategy())
                        .policyParameters(requestDetails.getPolicyParameters())
                        .slotId(requestDetails.getSlotId())
                        .slotLeaseTtlSeconds(requestDetails.getSlotLeaseTtlSeconds())
                        .build();

        // Try to add to queue first
        boolean added = queue.enqueue(req);

        if (!added) {
            throw new IllegalStateException(
                    "Queue is full. Request cannot be added.");
        }

        // Store only after successful queue insertion
        requestStore.put(req.getRequestId(), req);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.put(idempotencyKey, req);
        }

        return req;
    }

    public synchronized EligibilityRequestEntity enqueue(
            String studentId, String driveId, String version, int priority,
            String correlationId, String idempotencyKey) {
        EligibilityRequestEntity details = EligibilityRequestEntity.builder()
                .chainingStrategy("sequential_and").slotLeaseTtlSeconds(300).build();
        return enqueue(null, studentId, driveId, version, priority, correlationId, idempotencyKey, details);
    }

    /** Backward compatible overload for existing demos and unit tests. */
    public synchronized EligibilityRequestEntity enqueue(
            String studentId, String driveId, String version, int priority, String correlationId,
            String idempotencyKey, EligibilityRequestEntity requestDetails) {
        return enqueue(null, studentId, driveId, version, priority, correlationId, idempotencyKey, requestDetails);
    }

    private boolean sameRequest(EligibilityRequestEntity existing, String applicationId, String studentId,
                                String driveId, String version, int priority, EligibilityRequestEntity details) {
        return java.util.Objects.equals(existing.getApplicationId(), applicationId)
                && java.util.Objects.equals(existing.getStudentId(), studentId)
                && java.util.Objects.equals(existing.getDriveId(), driveId)
                && java.util.Objects.equals(existing.getRuleSetVersion(), version)
                && existing.getPriority() == priority
                && java.util.Objects.equals(existing.getStudent(), details.getStudent())
                && java.util.Objects.equals(existing.getRuleSet(), details.getRuleSet())
                && java.util.Objects.equals(existing.getChainingStrategy(), details.getChainingStrategy())
                && java.util.Objects.equals(existing.getPolicyParameters(), details.getPolicyParameters())
                && java.util.Objects.equals(existing.getSlotId(), details.getSlotId())
                && java.util.Objects.equals(existing.getSlotLeaseTtlSeconds(), details.getSlotLeaseTtlSeconds());
    }

    public synchronized EligibilityRequestEntity dequeueNext() {
        return queue.dequeue();
    }

    public boolean hasIdempotencyKey(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank()
                && idempotencyStore.containsKey(idempotencyKey);
    }

    public EligibilityRequestEntity getRequest(String requestId) {
        return requestStore.get(requestId);
    }

    public int getPosition(String requestId) {
        return queue.getPosition(requestId);
    }

    public int getDepth() {
        return queue.size();
    }

    public int getDepthForDrive(String driveId) {
        return (int) requestStore.values().stream()
                .filter(r -> java.util.Objects.equals(driveId, r.getDriveId()))
                .filter(r -> r.getState() == RequestState.QUEUED || r.getState() == RequestState.PROCESSING)
                .count();
    }

    public String getStrategyName() {
        return queue.getStrategyName();
    }
}
