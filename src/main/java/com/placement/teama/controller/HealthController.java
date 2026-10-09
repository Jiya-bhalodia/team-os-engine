package com.placement.teama.controller;

import com.placement.teama.dto.ApiResponse;
import com.placement.teama.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    private final QueueService queueService;
    private final String buildVersion;
    public HealthController(QueueService queueService,
                            @Value("${teama.build.version:unknown}") String buildVersion) {
        this.queueService = queueService;
        this.buildVersion = buildVersion == null || buildVersion.isBlank() ? "unknown" : buildVersion;
    }
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP", "service", "Team A OS Engine",
                "timestamp", Instant.now().toString(), "build_version", buildVersion), "health"));
    }
    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ready() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "READY", "queue_strategy", queueService.getStrategyName()), "ready"));
    }
}
