package com.placement.teama.service;

import com.placement.teama.concurrency.LockManager;
import com.placement.teama.model.entity.EligibilityRequestEntity;
import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.InterviewSlot;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import com.placement.teama.model.enums.RequestState;
import com.placement.teama.model.enums.SlotState;
import com.placement.teama.telemetry.TelemetryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EligibilityLifecycleWorkerTest {
    @Test
    void eligibleRequestIsEvaluatedStoredAndAllocated() {
        QueueService queue = new QueueService("fifo", 10);
        DecisionStoreService decisions = new DecisionStoreService();
        LockManager locks = new LockManager();
        locks.registerSlot(InterviewSlot.builder().slotId("SLOT-1").driveId("DRIVE-1")
                .capacity(1).state(SlotState.AVAILABLE).build());
        EligibilityLifecycleWorker worker = new EligibilityLifecycleWorker(queue, new EligibilityService(),
                decisions, locks, new TelemetryService());

        EligibilityRequestEntity request = queue.enqueue("STU-1", "DRIVE-1", "v1", 1, "corr-1", "key-1",
                details(student(8.5, 0), rules(), "SLOT-1"));
        worker.process(queue.dequeueNext());

        assertEquals(RequestState.ALLOCATED, request.getState());
        assertNotNull(request.getDecisionId());
        assertNotNull(request.getLeaseId());
        assertEquals(request.getRequestId(), decisions.find(request.getDecisionId()).getRequestId());
    }

    @Test
    void ineligibleRequestStopsBeforeSlotAllocation() {
        QueueService queue = new QueueService("fifo", 10);
        DecisionStoreService decisions = new DecisionStoreService();
        TelemetryService telemetry = new TelemetryService();
        EligibilityLifecycleWorker worker = new EligibilityLifecycleWorker(queue, new EligibilityService(),
                decisions, new LockManager(), telemetry);

        EligibilityRequestEntity request = queue.enqueue("STU-2", "DRIVE-1", "v1", 1, "corr-2", "key-2",
                details(student(6.0, 0), rules(), null));
        worker.process(queue.dequeueNext());

        assertEquals(RequestState.NOT_ELIGIBLE, request.getState());
        assertNotNull(request.getDecisionId());
        assertNull(request.getLeaseId());
        java.util.Map<String, Object> metrics = telemetry.getMetricsSummary(0);
        assertEquals(1L, metrics.get("completed_requests"));
        assertEquals(0L, metrics.get("failed_requests"));
    }

    @Test
    void idempotencyKeyRejectsDifferentRequestData() {
        QueueService queue = new QueueService("fifo", 10);
        queue.enqueue("STU-1", "DRIVE-1", "v1", 1, "corr", "same-key", details(student(8, 0), rules(), null));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue("STU-2", "DRIVE-1", "v1", 1,
                "corr", "same-key", details(student(8, 0), rules(), null)));
    }

    private EligibilityRequestEntity details(StudentSnapshot student, RuleSet rules, String slotId) {
        return EligibilityRequestEntity.builder().student(student).ruleSet(rules)
                .chainingStrategy("sequential_and").slotId(slotId).slotLeaseTtlSeconds(60).build();
    }

    private StudentSnapshot student(double cgpa, int backlogs) {
        return StudentSnapshot.builder().studentId("STUDENT").cgpa(cgpa).backlogs(backlogs)
                .branch("CSE").attendancePct(90).skills(List.of("Java")).build();
    }

    private RuleSet rules() {
        return RuleSet.builder().version("v1").rules(List.of(
                new EligibilityRule("CGPA", "min_cgpa", 7.5, 1.0),
                new EligibilityRule("BACKLOG", "max_backlogs", 0, 1.0))).build();
    }
}
