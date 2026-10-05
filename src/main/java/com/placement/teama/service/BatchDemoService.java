package com.placement.teama.service;

import com.placement.teama.concurrency.LockManager;
import com.placement.teama.dto.BatchEvaluationRequestDto;
import com.placement.teama.dto.BatchEvaluationResponse;
import com.placement.teama.dto.BatchStudentRequestDto;
import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.model.entity.EligibilityRequestEntity;
import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.InterviewSlot;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import com.placement.teama.model.enums.EligibilityResult;
import com.placement.teama.model.enums.RequestState;
import com.placement.teama.model.enums.SlotState;
import com.placement.teama.telemetry.TelemetryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Batch layer: every entry is still placed through QueueService and EligibilityLifecycleWorker. */
@Service
public class BatchDemoService {
    private static final int DEMO_WORKERS = 4;
    private final QueueService queueService;
    private final EligibilityLifecycleWorker lifecycleWorker;
    private final LockManager lockManager;
    private final TelemetryService telemetryService;
    private final DecisionStoreService decisionStoreService;
    private final ExecutorService executor = Executors.newFixedThreadPool(DEMO_WORKERS);
    private final Map<String, BatchRecord> batches = new ConcurrentHashMap<>();

    public BatchDemoService(QueueService queueService, EligibilityLifecycleWorker lifecycleWorker,
                            LockManager lockManager, TelemetryService telemetryService,
                            DecisionStoreService decisionStoreService) {
        this.queueService = queueService;
        this.lifecycleWorker = lifecycleWorker;
        this.lockManager = lockManager;
        this.telemetryService = telemetryService;
        this.decisionStoreService = decisionStoreService;
    }

    public BatchEvaluationResponse submit(BatchEvaluationRequestDto body, String correlationId) {
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8);
        BatchRecord batch = new BatchRecord(batchId, Instant.now());
        batches.put(batchId, batch);
        int index = 0;
        for (BatchStudentRequestDto entry : body.getStudents()) {
            if (entry.getStudent() == null || entry.getStudent().getStudentId() == null || entry.getStudent().getStudentId().isBlank()) {
                throw new IllegalArgumentException("Every batch student requires student.studentId");
            }
            String key = batchId + "-" + index++;
            EligibilityRequestEntity details = EligibilityRequestEntity.builder()
                    .student(entry.getStudent()).ruleSet(body.getRuleSet())
                    .chainingStrategy(body.getChainingStrategy()).policyParameters(body.getPolicyParameters())
                    .slotId(entry.getSlotId()).slotLeaseTtlSeconds(entry.getSlotLeaseTtlSeconds()).build();
            EligibilityRequestEntity request = queueService.enqueue(entry.getStudent().getStudentId(), body.getDriveId(),
                    body.getRuleSetVersion(), entry.getPriority(), correlationId, key, details);
            batch.requestIds.add(request.getRequestId());
            telemetryService.recordQueued();
        }
        for (int worker = 0; worker < DEMO_WORKERS; worker++) executor.submit(this::drainQueue);
        return response(batch);
    }

    private void drainQueue() {
        EligibilityRequestEntity request;
        while ((request = queueService.dequeueNext()) != null) lifecycleWorker.process(request);
    }

    public BatchEvaluationResponse find(String batchId) {
        BatchRecord batch = batches.get(batchId);
        if (batch == null) return null;
        return response(batch);
    }

    public String report(String batchId) {
        BatchEvaluationResponse response = find(batchId);
        if (response == null) return null;
        Map<String, Object> s = response.getSummary();
        Map<String, Object> m = response.getMetrics();
        StringBuilder out = new StringBuilder();
        out.append("RULE-BASED PLACEMENT OS ENGINE\nBATCH DEMONSTRATION\n\n");
        out.append("Batch ID                 : ").append(response.getBatchId()).append('\n');
        out.append("Status                   : ").append(response.getStatus()).append('\n');
        out.append("Students submitted       : ").append(response.getTotalRequests()).append('\n');
        out.append("Queue strategy           : ").append(response.getQueueStrategy()).append('\n');
        out.append("Eligible / Conditional / Not eligible : ").append(s.get("eligible")).append(" / ")
                .append(s.get("conditional")).append(" / ").append(s.get("notEligible")).append('\n');
        out.append("Failed requests          : ").append(s.get("failed")).append('\n');
        out.append("Processing time          : ").append(m.get("processingTimeMs")).append(" ms\n");
        out.append("Average evaluation time  : ").append(m.get("averageEvaluationTimeMs")).append(" ms\n");
        out.append("Throughput               : ").append(m.get("throughputRequestsPerSecond")).append(" requests/sec\n\n");
        out.append("STUDENT RESULTS\n");
        for (BatchEvaluationResponse.StudentResult r : response.getStudents()) {
            out.append(String.format("%-12s %-14s %-16s %s%n", r.getStudentId(), r.getState(),
                    r.getResult() == null ? "-" : r.getResult(), r.getSlotId() == null ? "" : r.getSlotId()));
        }
        return out.toString();
    }

    /** Safe controlled cycle: the graph is populated, detected and cleared without blocking a thread. */
    public Map<String, Object> runDeadlockDemo() {
        List<String> cycle = lockManager.runControlledDeadlockDemo("DEMO-REQUEST-A", "DEMO-REQUEST-B");
        if (!cycle.isEmpty()) {
            telemetryService.recordDeadlock();
            telemetryService.recordDeadlockResolved();
        }
        return Map.of("deadlockDetected", !cycle.isEmpty(), "cycle", cycle,
                "recoveryAction", "WAIT_FOR_EDGES_CLEARED_AND_REQUESTS_ABORTED");
    }

    /** One-command demo: 20 requests, real queue workers, real slot conflict, then safe deadlock detection. */
    public Map<String, Object> runFullDemo(String correlationId) {
        try {
            lockManager.registerSlot(InterviewSlot.builder().slotId("DEMO-SLOT-A").driveId("DRV-DEMO-001")
                    .capacity(1).state(SlotState.AVAILABLE).build());
        } catch (IllegalArgumentException ignored) { /* Re-running demo keeps its already registered demo slot. */ }
        BatchEvaluationRequestDto request = generatedDemoRequest();
        BatchEvaluationResponse batch = submit(request, correlationId);
        Map<String, Object> deadlock = runDeadlockDemo();
        return Map.of("batch", batch, "deadlockDemo", deadlock,
                "reportUrl", "/api/v1/demo/batch-evaluation/" + batch.getBatchId() + "/report");
    }

    private BatchEvaluationResponse response(BatchRecord batch) {
        List<BatchEvaluationResponse.StudentResult> results = new ArrayList<>();
        int eligible = 0, conditional = 0, notEligible = 0, failed = 0, complete = 0;
        double evaluationTotal = 0;
        int evaluationCount = 0;
        for (String id : batch.requestIds) {
            EligibilityRequestEntity r = queueService.getRequest(id);
            if (r == null) continue;
            if (r.getState() != RequestState.QUEUED && r.getState() != RequestState.PROCESSING && r.getState() != RequestState.SLOT_ALLOCATION_PENDING) complete++;
            if (r.getState() == RequestState.FAILED) failed++;
            if (r.getEligibilityResult() == EligibilityResult.ELIGIBLE) eligible++;
            if (r.getEligibilityResult() == EligibilityResult.CONDITIONAL) conditional++;
            if (r.getEligibilityResult() == EligibilityResult.NOT_ELIGIBLE) notEligible++;
            if (r.getDecisionId() != null) {
                EligibilityDecisionResponse decision = decisionStoreService.find(r.getDecisionId());
                if (decision != null) {
                    evaluationTotal += decision.getDecisionMetrics().getEvaluationTimeMs();
                    evaluationCount++;
                }
            }
            results.add(BatchEvaluationResponse.StudentResult.builder().studentId(r.getStudentId()).requestId(r.getRequestId())
                    .decisionId(r.getDecisionId()).state(r.getState().name())
                    .result(r.getEligibilityResult() == null ? null : r.getEligibilityResult().name())
                    .slotId(r.getSlotId()).leaseId(r.getLeaseId()).errorMessage(r.getErrorMessage()).build());
        }
        boolean finished = complete == batch.requestIds.size();
        long duration = Math.max(1, (finished ? Instant.now() : Instant.now()).toEpochMilli() - batch.startedAt.toEpochMilli());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", batch.requestIds.size()); summary.put("completed", complete); summary.put("eligible", eligible);
        summary.put("conditional", conditional); summary.put("notEligible", notEligible); summary.put("failed", failed);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("processingTimeMs", duration);
        metrics.put("averageEvaluationTimeMs", evaluationCount == 0 ? 0.0 : evaluationTotal / evaluationCount);
        metrics.put("throughputRequestsPerSecond", Math.round((complete * 1000.0 / duration) * 100.0) / 100.0);
        metrics.put("runtime", telemetryService.getMetricsSummary(queueService.getDepth()));
        return BatchEvaluationResponse.builder().batchId(batch.batchId).status(finished ? "COMPLETED" : "PROCESSING")
                .totalRequests(batch.requestIds.size()).queueStrategy(queueService.getStrategyName())
                .summary(summary).metrics(metrics).students(results).build();
    }

    private BatchEvaluationRequestDto generatedDemoRequest() {
        BatchEvaluationRequestDto body = new BatchEvaluationRequestDto();
        body.setDriveId("DRV-DEMO-001"); body.setRuleSetVersion("v1.0"); body.setChainingStrategy("decision_tree");
        body.setRuleSet(RuleSet.builder().version("v1.0").rules(List.of(
                new EligibilityRule("CGPA", "min_cgpa", 7.5, 1.0),
                new EligibilityRule("BACKLOG", "max_backlogs", 0, 1.0),
                new EligibilityRule("BRANCH", "allowed_branches", List.of("CSE", "IT"), 1.0),
                new EligibilityRule("SKILLS", "required_skills", List.of("Java"), 1.0))).build());
        List<BatchStudentRequestDto> students = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            boolean rejected = i > 12;
            boolean conditional = i >= 9 && i <= 12;
            StudentSnapshot student = StudentSnapshot.builder().studentId(String.format("DEMO-STU-%02d", i))
                    .cgpa(rejected ? 6.5 : 8.2).backlogs(rejected ? 1 : 0)
                    .branch(conditional ? "ECE" : "CSE").attendancePct(90).skills(List.of("Java")).build();
            BatchStudentRequestDto entry = new BatchStudentRequestDto(); entry.setStudent(student); entry.setPriority(21 - i);
            if (i <= 3) entry.setSlotId("DEMO-SLOT-A");
            students.add(entry);
        }
        body.setStudents(students); return body;
    }

    private static class BatchRecord {
        private final String batchId; private final Instant startedAt; private final List<String> requestIds = new ArrayList<>();
        private BatchRecord(String batchId, Instant startedAt) { this.batchId = batchId; this.startedAt = startedAt; }
    }
}
