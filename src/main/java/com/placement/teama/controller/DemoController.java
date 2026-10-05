package com.placement.teama.controller;

import com.placement.teama.dto.ApiResponse;
import com.placement.teama.dto.BatchEvaluationRequestDto;
import com.placement.teama.dto.BatchEvaluationResponse;
import com.placement.teama.service.BatchDemoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class DemoController {
    private final BatchDemoService batchDemoService;

    public DemoController(BatchDemoService batchDemoService) { this.batchDemoService = batchDemoService; }

    @PostMapping("/api/v1/demo/batch-evaluation")
    public ResponseEntity<ApiResponse<BatchEvaluationResponse>> submit(@Valid @RequestBody BatchEvaluationRequestDto body,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String corr = validCorrelation(correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batchDemoService.submit(body, corr), corr));
    }

    @GetMapping("/api/v1/demo/batch-evaluation/{batchId}")
    public ResponseEntity<ApiResponse<BatchEvaluationResponse>> result(@PathVariable String batchId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String corr = validCorrelation(correlationId);
        BatchEvaluationResponse response = batchDemoService.find(batchId);
        if (response == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("BATCH_NOT_FOUND", "Batch not found: " + batchId, corr));
        return ResponseEntity.ok(ApiResponse.success(response, corr));
    }

    @GetMapping(value = "/api/v1/demo/batch-evaluation/{batchId}/report", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> report(@PathVariable String batchId) {
        String report = batchDemoService.report(batchId);
        return report == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(report);
    }

    @PostMapping("/api/v1/demo/deadlock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deadlock(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String corr = validCorrelation(correlationId);
        return ResponseEntity.ok(ApiResponse.success(batchDemoService.runDeadlockDemo(), corr));
    }

    @PostMapping("/api/v1/demo/run")
    public ResponseEntity<ApiResponse<Map<String, Object>>> run(
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String corr = validCorrelation(correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batchDemoService.runFullDemo(corr), corr));
    }

    private String validCorrelation(String value) { return value == null || value.isBlank() ? "corr-" + UUID.randomUUID() : value; }
}
