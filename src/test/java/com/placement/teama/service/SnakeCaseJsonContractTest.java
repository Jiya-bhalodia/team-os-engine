package com.placement.teama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placement.teama.concurrency.LockManager;
import com.placement.teama.controller.EligibilityController;
import com.placement.teama.controller.HealthController;
import com.placement.teama.controller.LockController;
import com.placement.teama.controller.MetricsController;
import com.placement.teama.controller.SlotController;
import com.placement.teama.dto.BatchEvaluationRequestDto;
import com.placement.teama.dto.BatchEvaluationResponse;
import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.exception.GlobalExceptionHandler;
import com.placement.teama.model.entity.InterviewSlot;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.SlotLease;
import com.placement.teama.model.entity.StudentSnapshot;
import com.placement.teama.model.enums.EligibilityResult;
import com.placement.teama.model.enums.SlotState;
import com.placement.teama.security.ServiceAuthenticationFilter;
import com.placement.teama.telemetry.TelemetryService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SnakeCaseJsonContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private QueueService queue;
    private DecisionStoreService decisions;
    private EligibilityService eligibilityService;
    private MockMvc eligibilityApi;

    @BeforeEach
    void setUp() {
        queue = new QueueService("fifo", 20);
        decisions = new DecisionStoreService();
        eligibilityService = new EligibilityService();
        EligibilityController controller = new EligibilityController(queue, eligibilityService,
                new TelemetryService(), decisions);
        eligibilityApi = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void eligibilityEndpointReadsSnakeCaseAndReturnsSnakeCaseWithIdempotency() throws Exception {
        String body = """
                {
                  "application_id":"APP-SNAKE-1",
                  "student_id":"STU-SNAKE-1",
                  "drive_id":"DRV-SNAKE-1",
                  "rule_set_version":"v1",
                  "priority":2,
                  "student":{"student_id":"STU-SNAKE-1","cgpa":8.4,"backlogs":0,"attendance_pct":91,"branch":"CSE","skills":["Java"]},
                  "rule_set":{"version":"v1","rules":[{"rule_id":"R1","rule_type":"min_cgpa","threshold":7.5,"weight":1.0}]},
                  "chaining_strategy":"sequential_and"
                }
                """;

        String firstResponse = eligibilityApi.perform(post("/api/v1/eligibility/requests")
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "snake-key-1")
                        .header("X-Correlation-ID", "corr-snake-1").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.application_id").value("APP-SNAKE-1"))
                .andExpect(jsonPath("$.data.student_id").value("STU-SNAKE-1"))
                .andExpect(jsonPath("$.data.drive_id").value("DRV-SNAKE-1"))
                .andExpect(jsonPath("$.data.rule_set_version").value("v1"))
                .andExpect(jsonPath("$.data.submitted_at").exists())
                .andExpect(jsonPath("$.data.applicationId").doesNotExist())
                .andExpect(jsonPath("$.meta.correlation_id").value("corr-snake-1"))
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(firstResponse).path("data").path("request_id").asText();
        assertFalse(requestId.isBlank());

        eligibilityApi.perform(post("/api/v1/eligibility/requests")
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "snake-key-1")
                        .header("X-Correlation-ID", "corr-snake-1").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request_id").value(requestId));

        eligibilityApi.perform(post("/api/v1/eligibility/requests")
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "snake-key-1")
                        .content(body.replace("APP-SNAKE-1", "APP-SNAKE-2")))
                .andExpect(status().isConflict());

        eligibilityApi.perform(get("/api/v1/eligibility/requests/{requestId}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request_id").value(requestId))
                .andExpect(jsonPath("$.data.queue_position").exists())
                .andExpect(jsonPath("$.data.estimated_evaluation_time_ms").exists())
                .andExpect(jsonPath("$.data.accepted_policy").exists())
                .andExpect(jsonPath("$.data.processingStartedAt").doesNotExist());
    }

    @Test
    void legacyCamelCaseRequestAliasesRemainAcceptedButResponsesAreSnakeCase() throws Exception {
        String body = """
                {"applicationId":"APP-OLD-1","studentId":"STU-OLD-1","driveId":"DRV-OLD-1","ruleSetVersion":"v1",
                 "student":{"studentId":"STU-OLD-1","cgpa":8.0,"backlogs":0,"attendancePct":90,"branch":"CSE","skills":[]},
                 "ruleSet":{"version":"v1","rules":[{"ruleId":"R1","ruleType":"min_cgpa","threshold":7.0}]},
                 "chainingStrategy":"sequential_and"}
                """;
        eligibilityApi.perform(post("/api/v1/eligibility/requests")
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "old-key-1").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.application_id").value("APP-OLD-1"))
                .andExpect(jsonPath("$.data.request_id").exists())
                .andExpect(jsonPath("$.data.applicationId").doesNotExist());
    }

    @Test
    void decisionEndpointUsesSnakeCaseIncludingNestedMetrics() throws Exception {
        EligibilityDecisionResponse decision = EligibilityDecisionResponse.builder()
                .decisionId("DEC-SNAKE-1").requestId("REQ-SNAKE-1").applicationId("APP-SNAKE-1")
                .correlationId("corr-snake-1").leaseId("LEASE-SNAKE-1")
                .eligibilityResult(EligibilityResult.ELIGIBLE).failedRules(List.of())
                .ruleSetVersion("v1").decisionMetrics(new EligibilityDecisionResponse.DecisionMetrics(2.5, 1))
                .build();
        decisions.save(decision);

        eligibilityApi.perform(get("/api/v1/eligibility/decisions/{decisionId}", "DEC-SNAKE-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision_id").value("DEC-SNAKE-1"))
                .andExpect(jsonPath("$.data.request_id").value("REQ-SNAKE-1"))
                .andExpect(jsonPath("$.data.application_id").value("APP-SNAKE-1"))
                .andExpect(jsonPath("$.data.eligibility_result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.failed_rules").isArray())
                .andExpect(jsonPath("$.data.rule_set_version").value("v1"))
                .andExpect(jsonPath("$.data.decision_metrics.evaluation_time_ms").value(2.5))
                .andExpect(jsonPath("$.data.decisionMetrics").doesNotExist())
                .andExpect(jsonPath("$.meta.correlation_id").value("corr-snake-1"));
    }

    @Test
    void directRuleEvaluationAcceptsAndReturnsSnakeCase() throws Exception {
        String body = """
                {"rule_set":{"version":"v1","rules":[{"rule_id":"R1","rule_type":"min_cgpa","threshold":7.0}]},
                 "student":{"student_id":"STU-2","cgpa":8.0,"backlogs":0,"branch":"CSE","attendance_pct":90,"skills":[]},
                 "chaining_strategy":"sequential_and"}
                """;

        eligibilityApi.perform(post("/internal/v1/rules/evaluate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision_id").exists())
                .andExpect(jsonPath("$.data.eligibility_result").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.rule_set_version").value("v1"))
                .andExpect(jsonPath("$.data.decision_metrics.rules_checked").value(1))
                .andExpect(jsonPath("$.data.decisionMetrics").doesNotExist())
                .andExpect(jsonPath("$.meta.correlation_id").exists());
    }

    @Test
    void nestedRuleAndStudentTypesSerializeSnakeCaseAndAcceptLegacyAliases() throws Exception {
        String snakeJson = """
                {"student":{"student_id":"STU-1","attendance_pct":88},
                 "rule_set":{"version":"v1","rules":[{"rule_id":"R1","rule_type":"min_cgpa","threshold":7.0}]}}
                """;
        JsonNode request = objectMapper.readTree(snakeJson);
        StudentSnapshot student = objectMapper.treeToValue(request.path("student"), StudentSnapshot.class);
        assertEquals("STU-1", student.getStudentId());
        assertEquals(88, student.getAttendancePct());

        StudentSnapshot legacyStudent = objectMapper.readValue(
                "{\"studentId\":\"STU-OLD\",\"attendancePct\":80}", StudentSnapshot.class);
        assertEquals("STU-OLD", legacyStudent.getStudentId());

        RuleSet rules = objectMapper.readValue(
                "{\"version\":\"v1\",\"rules\":[{\"ruleId\":\"R2\",\"ruleType\":\"max_backlogs\",\"threshold\":0}]}",
                RuleSet.class);
        JsonNode ruleJson = objectMapper.valueToTree(rules);
        assertEquals("R2", ruleJson.path("rules").get(0).path("rule_id").asText());
        assertTrue(ruleJson.path("rules").get(0).has("rule_type"));
        assertFalse(ruleJson.path("rules").get(0).has("ruleType"));
    }

    @Test
    void lockApiUsesSnakeCaseForRequestAndLeaseResponse() throws Exception {
        LockManager lockManager = new LockManager();
        lockManager.registerSlot(InterviewSlot.builder().slotId("S-1").driveId("D-1").capacity(1)
                .state(SlotState.AVAILABLE).build());
        MockMvc lockApi = MockMvcBuilders.standaloneSetup(new LockController(lockManager, new TelemetryService())).build();

        String response = lockApi.perform(post("/internal/v1/locks/acquire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slot_id\":\"S-1\",\"student_id\":\"STU-1\",\"ttl_seconds\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lease_id").exists())
                .andExpect(jsonPath("$.data.slot_id").value("S-1"))
                .andExpect(jsonPath("$.data.holder_id").value("STU-1"))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String leaseId = objectMapper.readTree(response).path("data").path("lease_id").asText();

        lockApi.perform(post("/internal/v1/locks/{leaseId}/commit", leaseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lease_id").value(leaseId));

        lockApi.perform(post("/internal/v1/deadlocks/analyse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wait_graph\":{\"A\":[\"B\"],\"B\":[\"A\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadlock_detected").value(true))
                .andExpect(jsonPath("$.data.deadlock_cycle").isArray())
                .andExpect(jsonPath("$.data.deadlockDetected").doesNotExist());
    }

    @Test
    void queueHealthAndDeadlockMapResponsesUseSnakeCase() throws Exception {
        MockMvc healthApi = MockMvcBuilders.standaloneSetup(new HealthController(queue)).build();
        healthApi.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queue_strategy").exists())
                .andExpect(jsonPath("$.data.queueStrategy").doesNotExist());

        eligibilityApi.perform(get("/api/v1/drives/{driveId}/queue", "DRV-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drive_id").value("DRV-1"))
                .andExpect(jsonPath("$.data.current_depth").exists())
                .andExpect(jsonPath("$.data.driveId").doesNotExist());
    }

    @Test
    void configuredServiceAuthenticationStillRejectsMissingBearerToken() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("test-service-token", objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/eligibility/requests/REQ-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(objectMapper.readTree(response.getContentAsString()).path("error").has("code"));
        assertTrue(objectMapper.readTree(response.getContentAsString()).path("meta").has("correlation_id"));
        assertFalse(objectMapper.readTree(response.getContentAsString()).path("meta").has("correlationId"));
    }

    @Test
    void exportedSlotAndBatchObjectsSerializeWithSnakeCaseProperties() throws Exception {
        SlotLease lease = SlotLease.builder().leaseId("L-1").slotId("S-1").holderId("STU-1")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        JsonNode leaseJson = objectMapper.valueToTree(lease);
        assertTrue(leaseJson.has("lease_id"));
        assertTrue(leaseJson.has("created_at"));
        assertFalse(leaseJson.has("leaseId"));

        StudentSnapshot student = StudentSnapshot.builder().studentId("STU-1").cgpa(8.0).backlogs(0)
                .branch("CSE").attendancePct(90).skills(List.of("Java")).build();
        JsonNode studentJson = objectMapper.valueToTree(student);
        assertTrue(studentJson.has("student_id"));
        assertTrue(studentJson.has("attendance_pct"));
        assertFalse(studentJson.has("studentId"));

        Map<String, Object> responseMap = Map.of("deadlock_detected", false, "deadlock_cycle", List.of());
        JsonNode mapJson = objectMapper.valueToTree(responseMap);
        assertTrue(mapJson.has("deadlock_detected"));
        assertFalse(mapJson.has("deadlockDetected"));

        BatchEvaluationRequestDto batchRequest = objectMapper.readValue("""
                {"drive_id":"DRV-1","rule_set_version":"v1","rule_set":{"version":"v1","rules":[{"rule_id":"R1","rule_type":"min_cgpa","threshold":7}]},
                 "chaining_strategy":"sequential_and","students":[{"student":{"student_id":"STU-1"},"slot_id":"S-1","slot_lease_ttl_seconds":30}]}
                """, BatchEvaluationRequestDto.class);
        assertEquals("DRV-1", batchRequest.getDriveId());
        assertEquals("STU-1", batchRequest.getStudents().get(0).getStudent().getStudentId());

        BatchEvaluationResponse batchResponse = BatchEvaluationResponse.builder().batchId("B-1")
                .status("COMPLETED").totalRequests(1).queueStrategy("FIFO")
                .summary(Map.of("not_eligible", 0)).metrics(Map.of("processing_time_ms", 5))
                .students(List.of(BatchEvaluationResponse.StudentResult.builder().studentId("STU-1")
                        .requestId("REQ-1").decisionId("DEC-1").leaseId("LEASE-1").build())).build();
        JsonNode batchJson = objectMapper.valueToTree(batchResponse);
        assertTrue(batchJson.has("batch_id"));
        assertTrue(batchJson.has("total_requests"));
        assertTrue(batchJson.has("queue_strategy"));
        assertTrue(batchJson.path("students").get(0).has("request_id"));
        assertFalse(batchJson.has("batchId"));
        assertFalse(batchJson.path("students").get(0).has("requestId"));

        MockMvc metricsApi = MockMvcBuilders.standaloneSetup(new MetricsController(new TelemetryService(), queue)).build();
        metricsApi.perform(get("/api/v1/metrics/eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queue_depth").exists())
                .andExpect(jsonPath("$.data.requests_processing_started").exists())
                .andExpect(jsonPath("$.data.average_rule_evaluation_time_ms").exists())
                .andExpect(jsonPath("$.meta.api_version").value("v1"));

        MockMvc slotsApi = MockMvcBuilders.standaloneSetup(new SlotController(new LockManager())).build();
        slotsApi.perform(post("/internal/v1/slots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slot_id\":\"S-2\",\"drive_id\":\"D-2\",\"start_time\":\"09:00\",\"capacity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slot_id").value("S-2"))
                .andExpect(jsonPath("$.data.drive_id").value("D-2"))
                .andExpect(jsonPath("$.data.start_time").value("09:00"))
                .andExpect(jsonPath("$.data.startTime").doesNotExist());
    }
}
