package com.placement.teama.controller;

import com.placement.teama.dto.ApiResponse;
import com.placement.teama.service.TeamCCallbackClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Operator-triggered, bounded redelivery for eligible callback failures. */
@RestController
public class TeamCCallbackController {
    private final TeamCCallbackClient callbackClient;

    public TeamCCallbackController(TeamCCallbackClient callbackClient) {
        this.callbackClient = callbackClient;
    }

    @GetMapping("/internal/v1/callbacks/{decisionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @PathVariable String decisionId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String corrId = correlationId == null || correlationId.isBlank()
                ? "corr-" + UUID.randomUUID() : correlationId;
        TeamCCallbackClient.DeliveryStatus deliveryStatus = callbackClient.getDeliveryStatus(decisionId);
        if (deliveryStatus == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                    "CALLBACK_DELIVERY_NOT_FOUND", "No callback delivery exists for this decision in this process", corrId));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "decision_id", decisionId,
                "delivery_status", deliveryStatus.name(),
                "redeliveries_remaining", callbackClient.getRemainingRedeliveries(decisionId)), corrId));
    }

    @PostMapping("/internal/v1/callbacks/{decisionId}/retry")
    public ResponseEntity<ApiResponse<Map<String, String>>> retry(
            @PathVariable String decisionId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        String corrId = correlationId == null || correlationId.isBlank()
                ? "corr-" + UUID.randomUUID() : correlationId;
        TeamCCallbackClient.RetryResult result = callbackClient.retryFailed(decisionId);
        if (result == TeamCCallbackClient.RetryResult.SCHEDULED) {
            return ResponseEntity.accepted().body(ApiResponse.success(
                    Map.of("decision_id", decisionId, "delivery_status", "PENDING"), corrId));
        }
        HttpStatus status = switch (result) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SCHEDULING_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case CALLBACK_DISABLED, RECEIVER_IDEMPOTENCY_UNCONFIRMED, NOT_RETRYABLE,
                    RETRY_LIMIT_REACHED -> HttpStatus.CONFLICT;
            case SCHEDULED -> throw new IllegalStateException("Handled scheduled retry above");
        };
        return ResponseEntity.status(status).body(ApiResponse.error(
                "CALLBACK_RETRY_" + result.name(), retryMessage(result), corrId));
    }

    private String retryMessage(TeamCCallbackClient.RetryResult result) {
        return switch (result) {
            case CALLBACK_DISABLED -> "Team C callback delivery is disabled";
            case RECEIVER_IDEMPOTENCY_UNCONFIRMED -> "Team C receiver idempotency must be confirmed before redelivery";
            case NOT_FOUND -> "No callback delivery exists for this decision in this process";
            case NOT_RETRYABLE -> "Callback is pending, delivered, or permanently rejected";
            case RETRY_LIMIT_REACHED -> "Callback redelivery limit has been reached";
            case SCHEDULING_FAILED -> "Callback redelivery could not be scheduled";
            case SCHEDULED -> "Callback redelivery scheduled";
        };
    }
}
