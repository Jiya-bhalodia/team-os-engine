package com.placement.teama.service;

import com.placement.teama.concurrency.LockManager;
import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.model.entity.EligibilityRequestEntity;
import com.placement.teama.model.entity.SlotLease;
import com.placement.teama.model.enums.EligibilityResult;
import com.placement.teama.model.enums.RequestState;
import com.placement.teama.telemetry.TelemetryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Connects the existing queue, rule engine and lock manager into one lifecycle. */
@Component
public class EligibilityLifecycleWorker {
    private static final Logger log = LoggerFactory.getLogger(EligibilityLifecycleWorker.class);
    private final QueueService queueService;
    private final EligibilityService eligibilityService;
    private final DecisionStoreService decisionStoreService;
    private final LockManager lockManager;
    private final TelemetryService telemetryService;
    private final TeamCDecisionCallback teamCCallback;

    /** Compatibility constructor for focused unit tests that do not exercise outbound delivery. */
    public EligibilityLifecycleWorker(QueueService queueService, EligibilityService eligibilityService,
                                      DecisionStoreService decisionStoreService, LockManager lockManager,
                                      TelemetryService telemetryService) {
        this(queueService, eligibilityService, decisionStoreService, lockManager, telemetryService,
                (applicationId, decision, correlationId) -> { });
    }

    @Autowired
    public EligibilityLifecycleWorker(QueueService queueService, EligibilityService eligibilityService,
                                      DecisionStoreService decisionStoreService, LockManager lockManager,
                                      TelemetryService telemetryService, TeamCDecisionCallback teamCCallback) {
        this.queueService = queueService;
        this.eligibilityService = eligibilityService;
        this.decisionStoreService = decisionStoreService;
        this.lockManager = lockManager;
        this.telemetryService = telemetryService;
        this.teamCCallback = teamCCallback;
    }

    @Scheduled(fixedDelayString = "${teama.worker.poll-ms:25}")
    public void processNextRequest() {
        EligibilityRequestEntity request = queueService.dequeueNext();
        if (request != null) process(request);
    }

    /** Public for deterministic integration tests and a future managed executor. */
    public void process(EligibilityRequestEntity request) {
        EligibilityDecisionResponse completedDecision = null;
        request.setState(RequestState.PROCESSING);
        request.setProcessingStartedAt(Instant.now());
        telemetryService.recordProcessingStarted();
        try {
            if (request.getStudent() == null || request.getRuleSet() == null) {
                throw new IllegalArgumentException("Student snapshot and rule set are required for lifecycle processing");
            }
            EligibilityDecisionResponse decision = eligibilityService.evaluate(
                    request.getRuleSet(), request.getStudent(), request.getChainingStrategy(), request.getPolicyParameters());
            decision.setRequestId(request.getRequestId());
            decision.setApplicationId(request.getApplicationId());
            decision.setCorrelationId(request.getCorrelationId());
            request.setFailedRules(decision.getFailedRules());
            decisionStoreService.save(decision);
            completedDecision = decision;
            request.setDecisionId(decision.getDecisionId());
            request.setEligibilityResult(decision.getEligibilityResult());
            request.setState(RequestState.EVALUATED);
            telemetryService.recordEvaluation(decision.getEligibilityResult().name(),
                    decision.getDecisionMetrics().getEvaluationTimeMs(), waitTime(request));

            if (decision.getEligibilityResult() == EligibilityResult.NOT_ELIGIBLE) {
                request.setState(RequestState.NOT_ELIGIBLE);
                request.setCompletedAt(Instant.now());
                telemetryService.recordCompleted();
                return;
            }
            if (decision.getEligibilityResult() == EligibilityResult.CONDITIONAL) {
                // Team C resolves waitlist/conditional outcomes; Team A must not lease a slot.
                request.setCompletedAt(Instant.now());
                telemetryService.recordCompleted();
                return;
            }
            if (request.getSlotId() == null || request.getSlotId().isBlank()) {
                request.setCompletedAt(Instant.now());
                telemetryService.recordCompleted();
                return;
            }
            request.setState(RequestState.SLOT_ALLOCATION_PENDING);
            telemetryService.recordLockAttempt();
            SlotLease lease = lockManager.acquireSlotLock(request.getSlotId(), request.getStudentId(),
                    request.getSlotLeaseTtlSeconds() == null ? 300 : request.getSlotLeaseTtlSeconds());
            if (lease == null) {
                request.setState(RequestState.FAILED);
                request.setErrorMessage("Interview slot is unavailable" + lockManager.deadlockSuffix());
                request.setCompletedAt(Instant.now());
                telemetryService.recordSlotAllocation(false);
                telemetryService.recordLockConflict();
                telemetryService.recordFailure();
                telemetryService.recordCompleted();
                return;
            }
            request.setLeaseId(lease.getLeaseId());
            decision.setLeaseId(lease.getLeaseId());
            decisionStoreService.save(decision);
            request.setState(RequestState.ALLOCATED);
            request.setCompletedAt(Instant.now());
            telemetryService.recordSlotAllocation(true);
            telemetryService.recordCompleted();
        } catch (Exception ex) {
            request.setState(RequestState.FAILED);
            request.setErrorMessage(ex.getMessage());
            request.setCompletedAt(Instant.now());
            telemetryService.recordFailure();
            telemetryService.recordCompleted();
        } finally {
            if (completedDecision != null && request.getCompletedAt() != null) {
                try {
                    teamCCallback.deliver(request.getApplicationId(), completedDecision, request.getCorrelationId());
                } catch (RuntimeException ex) {
                    log.warn("Could not schedule Team C callback: decision_id={}, error={}",
                            completedDecision.getDecisionId(), ex.getClass().getSimpleName());
                }
            }
        }
    }

    private double waitTime(EligibilityRequestEntity request) {
        return Math.max(0, Instant.now().toEpochMilli() - request.getSubmittedAt().toEpochMilli());
    }
}
