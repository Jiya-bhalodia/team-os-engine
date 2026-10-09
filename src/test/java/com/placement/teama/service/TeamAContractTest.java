package com.placement.teama.service;

import com.placement.teama.concurrency.LockManager;
import com.placement.teama.model.entity.*;
import com.placement.teama.model.enums.*;
import com.placement.teama.telemetry.TelemetryService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TeamAContractTest {
    @Test void applicationIdAndCorrelationReachDecision() {
        QueueService q = new QueueService("fifo", 10); DecisionStoreService d = new DecisionStoreService();
        EligibilityLifecycleWorker w = new EligibilityLifecycleWorker(q, new EligibilityService(), d, new LockManager(), new TelemetryService());
        EligibilityRequestEntity r = q.enqueue("APP-1", "STU-1", "DRV-1", "v1", 1, "corr-1", "key-1", details(8.0));
        w.process(q.dequeueNext());
        assertEquals("APP-1", r.getApplicationId());
        assertEquals("corr-1", d.find(r.getDecisionId()).getCorrelationId());
        assertEquals("APP-1", d.find(r.getDecisionId()).getApplicationId());
    }
    @Test void conditionalDoesNotAllocateLease() {
        QueueService q = new QueueService("fifo", 10); DecisionStoreService d = new DecisionStoreService(); LockManager l = new LockManager();
        l.registerSlot(InterviewSlot.builder().slotId("S").driveId("D").capacity(1).state(SlotState.AVAILABLE).build());
        EligibilityLifecycleWorker w = new EligibilityLifecycleWorker(q, new EligibilityService(), d, l, new TelemetryService());
        RuleSet conditional = RuleSet.builder().version("v1").rules(List.of(new EligibilityRule("BR", "allowed_branches", List.of("IT"), 1))).build();
        EligibilityRequestEntity r=q.enqueue("APP-2","STU-2","D","v1",1,"corr-2","key-2",EligibilityRequestEntity.builder().student(student(8)).ruleSet(conditional).chainingStrategy("decision_tree").slotId("S").slotLeaseTtlSeconds(60).build());
        w.process(q.dequeueNext());
        assertEquals(EligibilityResult.CONDITIONAL, r.getEligibilityResult()); assertNull(r.getLeaseId()); assertEquals(RequestState.EVALUATED, r.getState());
    }
    @Test void committedLeaseCannotBeReleased() throws Exception {
        LockManager l=new LockManager(); l.registerSlot(InterviewSlot.builder().slotId("S").driveId("D").capacity(1).state(SlotState.AVAILABLE).build());
        SlotLease lease=l.acquireSlotLock("S","STU",60); assertTrue(l.commitSlotLease(lease.getLeaseId())); assertFalse(l.releaseSlotLock(lease.getLeaseId()));
    }
    private EligibilityRequestEntity details(double cgpa) { return EligibilityRequestEntity.builder().student(student(cgpa)).ruleSet(rules()).chainingStrategy("sequential_and").slotLeaseTtlSeconds(60).build(); }
    private StudentSnapshot student(double cgpa) { return StudentSnapshot.builder().studentId("STU-1").cgpa(cgpa).backlogs(0).branch("CSE").attendancePct(90.0).skills(List.of("Java")).build(); }
    private RuleSet rules() { return RuleSet.builder().version("v1").rules(List.of(new EligibilityRule("CGPA","min_cgpa",7.5,1))).build(); }
}
