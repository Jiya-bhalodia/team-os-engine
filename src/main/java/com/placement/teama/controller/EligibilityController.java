package com.placement.teama.controller;

import com.placement.teama.dto.*;
import com.placement.teama.model.entity.EligibilityRequestEntity;
import com.placement.teama.service.DecisionStoreService;
import com.placement.teama.service.EligibilityService;
import com.placement.teama.service.QueueService;
import com.placement.teama.telemetry.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class EligibilityController {
    private final QueueService queueService;
    private final EligibilityService eligibilityService;
    private final TelemetryService telemetryService;
    private final DecisionStoreService decisionStoreService;

    public EligibilityController(QueueService queueService, EligibilityService eligibilityService,
            TelemetryService telemetryService, DecisionStoreService decisionStoreService) {
        this.queueService = queueService; this.eligibilityService = eligibilityService;
        this.telemetryService = telemetryService; this.decisionStoreService = decisionStoreService;
    }

    @PostMapping("/api/v1/eligibility/requests")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> submitRequest(
            @Valid @RequestBody CreateEligibilityRequestDto body,
            @RequestHeader(value = "X-Correlation-ID", required = false) String corrId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (corrId == null || corrId.isBlank()) corrId = "corr-" + java.util.UUID.randomUUID();
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key header is required");
        validate(body);
        boolean replay = queueService.hasIdempotencyKey(idempotencyKey);
        EligibilityRequestEntity req = queueService.enqueue(body.getApplicationId(), body.getStudentId(), body.getDriveId(),
                body.getRuleSetVersion(), body.getPriority(), corrId, idempotencyKey,
                EligibilityRequestEntity.builder().student(body.getStudent()).ruleSet(body.getRuleSet())
                        .chainingStrategy(body.getChainingStrategy()).policyParameters(body.getPolicyParameters())
                        .slotId(body.getSlotId()).slotLeaseTtlSeconds(body.getSlotLeaseTtlSeconds()).build());
        if (!replay) telemetryService.recordQueued();
        return ResponseEntity.status(replay ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(statusFor(req, queueService.getPosition(req.getRequestId())), req.getCorrelationId()));
    }

    private void validate(CreateEligibilityRequestDto body) {
        if (body.getStudent() == null || body.getStudent().getStudentId() == null || body.getStudent().getStudentId().isBlank())
            throw new IllegalArgumentException("student.studentId is required");
        if (!body.getStudentId().equals(body.getStudent().getStudentId())) throw new IllegalArgumentException("studentId must match student.studentId");
        if (body.getRuleSet() == null || body.getRuleSet().getVersion() == null || body.getRuleSet().getVersion().isBlank()
                || body.getRuleSet().getRules() == null || body.getRuleSet().getRules().isEmpty())
            throw new IllegalArgumentException("A non-empty versioned ruleSet is required");
        if (!body.getRuleSetVersion().equals(body.getRuleSet().getVersion()))
            throw new IllegalArgumentException("ruleSetVersion must match ruleSet.version");
        if (!eligibilityService.supportsStrategy(body.getChainingStrategy()))
            throw new IllegalArgumentException("Unsupported rule strategy: " + body.getChainingStrategy());
        if (body.getSlotLeaseTtlSeconds() != null && body.getSlotLeaseTtlSeconds() <= 0)
            throw new IllegalArgumentException("slotLeaseTtlSeconds must be greater than zero");
    }

    @GetMapping("/api/v1/drives/{driveId}/queue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDriveQueue(@PathVariable String driveId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String corrId) {
        if (corrId == null || corrId.isBlank()) corrId = "corr-" + java.util.UUID.randomUUID();
        return ResponseEntity.ok(ApiResponse.success(Map.of("driveId", driveId,
                "currentDepth", queueService.getDepthForDrive(driveId), "strategy", queueService.getStrategyName()), corrId));
    }

    @PostMapping("/internal/v1/rules/evaluate")
    public ResponseEntity<ApiResponse<EligibilityDecisionResponse>> evaluateRules(@Valid @RequestBody EvaluateRulesRequestDto body,
            @RequestHeader(value = "X-Correlation-ID", required = false) String corrId) {
        if (corrId == null || corrId.isBlank()) corrId = "corr-" + java.util.UUID.randomUUID();
        EligibilityDecisionResponse response = eligibilityService.evaluate(body.getRuleSet(), body.getStudent(), body.getChainingStrategy(), body.getPolicyParameters());
        response.setCorrelationId(corrId); decisionStoreService.save(response);
        telemetryService.recordEvaluation(response.getEligibilityResult().name(), response.getDecisionMetrics().getEvaluationTimeMs(), 0.0);
        return ResponseEntity.ok(ApiResponse.success(response, corrId));
    }

    @GetMapping("/api/v1/eligibility/requests/{requestId}")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getRequestStatus(@PathVariable String requestId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String ignoredCorrelationId) {
        EligibilityRequestEntity req = queueService.getRequest(requestId);
        String corrId = req == null ? (ignoredCorrelationId == null ? "corr-" + java.util.UUID.randomUUID() : ignoredCorrelationId) : req.getCorrelationId();
        if (req == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("REQUEST_NOT_FOUND", "Eligibility request not found: " + requestId, corrId));
        return ResponseEntity.ok(ApiResponse.success(statusFor(req, queueService.getPosition(requestId)), corrId));
    }

    @GetMapping("/api/v1/eligibility/decisions/{decisionId}")
    public ResponseEntity<ApiResponse<EligibilityDecisionResponse>> getDecision(@PathVariable String decisionId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String corrId) {
        EligibilityDecisionResponse decision = decisionStoreService.find(decisionId);
        if (decision == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("DECISION_NOT_FOUND", "Eligibility decision not found: " + decisionId, corrId == null ? "corr-" + java.util.UUID.randomUUID() : corrId));
        return ResponseEntity.ok(ApiResponse.success(decision, decision.getCorrelationId()));
    }

    private QueueStatusResponse statusFor(EligibilityRequestEntity request, int position) {
        return QueueStatusResponse.builder().applicationId(request.getApplicationId()).requestId(request.getRequestId())
                .studentId(request.getStudentId()).driveId(request.getDriveId()).queuePosition(Math.max(position, 0))
                .state(request.getState().name()).estimatedEvaluationTimeMs(Math.max(position, 0) * 15.0)
                .acceptedPolicy(queueService.getStrategyName()).correlationId(request.getCorrelationId())
                .ruleSetVersion(request.getRuleSetVersion()).decisionId(request.getDecisionId())
                .eligibilityResult(request.getEligibilityResult() == null ? null : request.getEligibilityResult().name())
                .failedRules(request.getFailedRules()).slotId(request.getSlotId()).leaseId(request.getLeaseId())
                .errorMessage(request.getErrorMessage()).submittedAt(request.getSubmittedAt())
                .processingStartedAt(request.getProcessingStartedAt()).completedAt(request.getCompletedAt()).build();
    }
}
