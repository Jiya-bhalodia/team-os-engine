package com.placement.teama.service;

import com.placement.teama.concurrency.LockManager;
import com.placement.teama.dto.BatchEvaluationRequestDto;
import com.placement.teama.dto.BatchEvaluationResponse;
import com.placement.teama.dto.BatchStudentRequestDto;
import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import com.placement.teama.telemetry.TelemetryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchDemoServiceTest {
    @Test
    void batchSubmitsTwentyNormalRequestsAndCompletesThem() throws InterruptedException {
        QueueService queue = new QueueService("heap", 100);
        TelemetryService telemetry = new TelemetryService();
        DecisionStoreService decisions = new DecisionStoreService();
        EligibilityLifecycleWorker worker = new EligibilityLifecycleWorker(queue, new EligibilityService(), decisions,
                new LockManager(), telemetry);
        BatchDemoService service = new BatchDemoService(queue, worker, new LockManager(), telemetry, decisions);

        BatchEvaluationResponse accepted = service.submit(batchRequest(), "corr-batch-test");
        assertEquals(20, accepted.getTotalRequests());
        for (int i = 0; i < 50 && !"COMPLETED".equals(service.find(accepted.getBatchId()).getStatus()); i++) {
            Thread.sleep(20);
        }
        BatchEvaluationResponse result = service.find(accepted.getBatchId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(20, result.getStudents().size());
        assertEquals(10, result.getSummary().get("eligible"));
        assertEquals(10, result.getSummary().get("notEligible"));
        assertTrue(result.getStudents().stream().allMatch(s -> s.getRequestId() != null && s.getDecisionId() != null));
    }

    @Test
    void controlledDeadlockIsDetectedAndRecovered() {
        QueueService queue = new QueueService("fifo", 10);
        TelemetryService telemetry = new TelemetryService();
        DecisionStoreService decisions = new DecisionStoreService();
        LockManager locks = new LockManager();
        BatchDemoService service = new BatchDemoService(queue,
                new EligibilityLifecycleWorker(queue, new EligibilityService(), decisions, locks, telemetry),
                locks, telemetry, decisions);
        assertEquals(true, service.runDeadlockDemo().get("deadlockDetected"));
        assertEquals(1L, telemetry.getMetricsSummary(0).get("anomalies") instanceof java.util.Map<?, ?> m
                ? m.get("deadlocks_detected") : -1L);
    }

    private BatchEvaluationRequestDto batchRequest() {
        BatchEvaluationRequestDto batch = new BatchEvaluationRequestDto();
        batch.setDriveId("DRV-BATCH"); batch.setRuleSetVersion("v1");
        batch.setRuleSet(RuleSet.builder().version("v1").rules(List.of(
                new EligibilityRule("R1", "min_cgpa", 7.5, 1.0))).build());
        List<BatchStudentRequestDto> students = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            BatchStudentRequestDto student = new BatchStudentRequestDto();
            student.setStudent(StudentSnapshot.builder().studentId("BATCH-" + i).cgpa(i < 10 ? 8.0 : 6.0)
                    .backlogs(0).branch("CSE").attendancePct(90).skills(List.of("Java")).build());
            student.setPriority(i); students.add(student);
        }
        batch.setStudents(students); return batch;
    }
}
