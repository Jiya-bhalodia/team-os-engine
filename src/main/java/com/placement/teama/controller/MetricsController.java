package com.placement.teama.controller;

import com.placement.teama.dto.ApiResponse;
import com.placement.teama.service.QueueService;
import com.placement.teama.telemetry.TelemetryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
public class MetricsController {

    private final TelemetryService telemetryService;
    private final QueueService queueService;

    public MetricsController(
            TelemetryService telemetryService,
            QueueService queueService) {

        this.telemetryService = telemetryService;
        this.queueService = queueService;
    }

    @GetMapping("/api/v1/metrics/eligibility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics(
            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false) String corrId) {

        if (corrId == null || corrId.isBlank()) {
            corrId = "corr-" + UUID.randomUUID();
        }

        Map<String, Object> metrics =
                telemetryService.getMetricsSummary(
                        queueService.getDepth());

        return ResponseEntity.ok(
                ApiResponse.success(
                        metrics,
                        corrId
                )
        );
    }

    @GetMapping(
            value = "/api/v1/stream/eligibility",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(
            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false) String corrId) {

        if (corrId == null || corrId.isBlank()) {
            corrId = "corr-" + UUID.randomUUID();
        }

        final String streamCorrId = corrId;

        SseEmitter emitter = new SseEmitter(0L);

        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {

            try {

                Map<String, Object> metrics =
                        telemetryService.getMetricsSummary(
                                queueService.getDepth());

                ApiResponse<Map<String, Object>> response =
                        ApiResponse.success(
                                metrics,
                                streamCorrId
                        );

                emitter.send(SseEmitter.event().data(response));

            } catch (IOException e) {

                emitter.completeWithError(e);
                executor.shutdown();
            }

        }, 0, 2, TimeUnit.SECONDS);

        emitter.onCompletion(executor::shutdown);

        emitter.onTimeout(executor::shutdown);

        emitter.onError(error ->
                executor.shutdown());

        return emitter;
    }
}