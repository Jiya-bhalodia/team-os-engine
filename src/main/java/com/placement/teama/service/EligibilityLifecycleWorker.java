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
        synchronized (request) {
            if (request.getCompletedAt() != null) {
                log.info("Eligibility processing skipped: request_id={}, correlation_id={}, reason=already_completed",
                        request.getRequestId(), request.getCorrelationId());
                return;
            }
            processOnce(request);
        }
    }

    private void processOnce(EligibilityRequestEntity request) {
        EligibilityDecisionResponse completedDecision = null;
        SlotLease acquiredLease = null;
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
            boolean slotIdPresent = request.getSlotId() != null && !request.getSlotId().isBlank();
            log.info("Eligibility decision evaluated: request_id={}, correlation_id={}, decision_id={}, result={}, slot_id_present={}",
                    request.getRequestId(), request.getCorrelationId(), decision.getDecisionId(),
                    decision.getEligibilityResult(), slotIdPresent);
            request.setFailedRules(decision.getFailedRules());
            decisionStoreService.save(decision);
            completedDecision = decision;
            request.setDecisionId(decision.getDecisionId());
            request.setEligibilityResult(decision.getEligibilityResult());
            request.setState(RequestState.EVALUATED);
            telemetryService.recordEvaluation(decision.getEligibilityResult().name(),
                    decision.getDecisionMetrics().getEvaluationTimeMs(), waitTime(request));

            if (decision.getEligibilityResult() == EligibilityResult.NOT_ELIGIBLE) {
                log.info("Slot lease not required: request_id={}, correlation_id={}, decision_id={}, acquisition_outcome=NOT_REQUIRED_FOR_RESULT",
                        request.getRequestId(), request.getCorrelationId(), decision.getDecisionId());
                request.setState(RequestState.NOT_ELIGIBLE);
                request.setCompletedAt(Instant.now());
                telemetryService.recordCompleted();
                return;
            }
            if (decision.getEligibilityResult() == EligibilityResult.CONDITIONAL) {
                // Team C resolves waitlist/conditional outcomes; Team A must not lease a slot.
                log.info("Slot lease not attempted: request_id={}, correlation_id={}, decision_id={}, acquisition_outcome=NOT_REQUIRED_FOR_RESULT",
                        request.getRequestId(), request.getCorrelationId(), decision.getDecisionId());
                request.setCompletedAt(Instant.now());
                telemetryService.recordCompleted();
                return;
            }
            if (request.getSlotId() == null || request.getSlotId().isBlank()) {
                log.warn("Slot lease not acquired: request_id={}, correlation_id={}, decision_id={}, slot_id_present=false, acquisition_outcome=NOT_ATTEMPTED_NO_SLOT_ID",
                        request.getRequestId(), request.getCorrelationId(), decision.getDecisionId());
                request.setCompletedAt(Instant.now());
                telemetryService.recordCompleted();
                return;
            }
            request.setState(RequestState.SLOT_ALLOCATION_PENDING);
            telemetryService.recordLockAttempt();
            log.info("Slot lease acquisition started: request_id={}, correlation_id={}, decision_id={}, slot_id_present=true",
                    request.getRequestId(), request.getCorrelationId(), decision.getDecisionId());
            SlotLease lease;
            try {
                lease = lockManager.acquireSlotLock(request.getSlotId(), request.getStudentId(),
                        request.getSlotLeaseTtlSeconds() == null ? 300 : request.getSlotLeaseTtlSeconds());
            } catch (InterruptedException ex) {
                log.warn("Slot lease acquisition ended: request_id={}, correlation_id={}, decision_id={}, acquisition_outcome=INTERRUPTED",
                        request.getRequestId(), request.getCorrelationId(), decision.getDecisionId());
                Thread.currentThread().interrupt();
                throw ex;
            } catch (RuntimeException ex) {
                log.warn("Slot lease acquisition ended: request_id={}, correlation_id={}, decision_id={}, acquisition_outcome=ERROR, error_type={}",
                        request.getRequestId(), request.getCorrelationId(), decision.getDecisionId(), ex.getClass().getSimpleName());
                throw ex;
            }
            if (lease == null) {
                log.warn("Slot lease acquisition ended: request_id={}, correlation_id={}, decision_id={}, acquisition_outcome=UNAVAILABLE",
                        request.getRequestId(), request.getCorrelationId(), decision.getDecisionId());
                request.setState(RequestState.FAILED);
                request.setErrorMessage("Interview slot is unavailable" + lockManager.deadlockSuffix());
                request.setCompletedAt(Instant.now());
                telemetryService.recordSlotAllocation(false);
                telemetryService.recordLockConflict();
                telemetryService.recordFailure();
                telemetryService.recordCompleted();
                return;
            }
            acquiredLease = lease;
            log.info("Slot lease acquisition ended: request_id={}, correlation_id={}, decision_id={}, acquisition_outcome=ACQUIRED",
                    request.getRequestId(), request.getCorrelationId(), decision.getDecisionId());
            request.setLeaseId(lease.getLeaseId());
            decision.setLeaseId(lease.getLeaseId());
            decisionStoreService.save(decision);
            request.setState(RequestState.ALLOCATED);
            request.setCompletedAt(Instant.now());
            telemetryService.recordSlotAllocation(true);
            telemetryService.recordCompleted();
        } catch (Exception ex) {
            if (acquiredLease != null) {
                boolean released;
                try {
                    released = lockManager.releaseSlotLock(acquiredLease.getLeaseId());
                } catch (RuntimeException releaseError) {
                    released = false;
                    log.error("Slot lease release failed after processing failure: request_id={}, correlation_id={}, decision_id={}, error_type={}",
                            request.getRequestId(), request.getCorrelationId(),
                            completedDecision == null ? null : completedDecision.getDecisionId(),
                            releaseError.getClass().getSimpleName());
                }
                request.setLeaseId(null);
                if (completedDecision != null) {
                    completedDecision.setLeaseId(null);
                    decisionStoreService.save(completedDecision);
                }
                log.warn("Slot lease released after processing failure: request_id={}, correlation_id={}, decision_id={}, release_succeeded={}",
                        request.getRequestId(), request.getCorrelationId(),
                        completedDecision == null ? null : completedDecision.getDecisionId(), released);
            }
            request.setState(RequestState.FAILED);
            request.setErrorMessage(ex.getMessage());
            request.setCompletedAt(Instant.now());
            telemetryService.recordFailure();
            telemetryService.recordCompleted();
        } finally {
            if (completedDecision != null && request.getCompletedAt() != null && teamCCallback.isEnabled()) {
                boolean allocatedWithLease = request.getState() == RequestState.ALLOCATED
                        && request.getLeaseId() != null && !request.getLeaseId().isBlank()
                        && request.getLeaseId().equals(completedDecision.getLeaseId());
                if (!allocatedWithLease) {
                    boolean leaseRequiredForDecision = completedDecision.getEligibilityResult() == EligibilityResult.ELIGIBLE;
                    String reason = leaseRequiredForDecision ? "required_lease_unavailable" : "result_does_not_allocate_slot";
                    if (leaseRequiredForDecision) recordCallbackMissingLease(request);
                    log.warn("Team C callback skipped: request_id={}, correlation_id={}, decision_id={}, result={}, callback_outcome=SKIPPED, reason={}, request_state={}",
                            request.getRequestId(), request.getCorrelationId(), completedDecision.getDecisionId(),
                            completedDecision.getEligibilityResult(), reason, request.getState());
                } else {
                    try {
                        teamCCallback.deliver(request.getApplicationId(), completedDecision, request.getCorrelationId());
                        log.info("Team C callback submitted: request_id={}, correlation_id={}, decision_id={}, callback_outcome=SUBMITTED",
                                request.getRequestId(), request.getCorrelationId(), completedDecision.getDecisionId());
                    } catch (RuntimeException ex) {
                        log.warn("Team C callback submission failed: request_id={}, correlation_id={}, decision_id={}, callback_outcome=FAILED, error_type={}",
                                request.getRequestId(), request.getCorrelationId(), completedDecision.getDecisionId(), ex.getClass().getSimpleName());
                    }
                }
            }
        }
    }

    private void recordCallbackMissingLease(EligibilityRequestEntity request) {
        String reason = "Team C callback not sent: required slot lease was not acquired";
        String existingError = request.getErrorMessage();
        if (existingError == null || existingError.isBlank()) {
            request.setErrorMessage(reason);
        } else if (!existingError.contains(reason)) {
            request.setErrorMessage(existingError + "; " + reason);
        }
    }

    private double waitTime(EligibilityRequestEntity request) {
        return Math.max(0, Instant.now().toEpochMilli() - request.getSubmittedAt().toEpochMilli());
    }
}
