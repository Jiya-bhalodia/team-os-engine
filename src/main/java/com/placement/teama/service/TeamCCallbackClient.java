package com.placement.teama.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.dto.TeamCEligibilityCallbackDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Best-effort callback client. Delivery records remain process-local. */
@Service
@EnableConfigurationProperties(TeamCCallbackProperties.class)
public class TeamCCallbackClient implements TeamCDecisionCallback, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TeamCCallbackClient.class);
    private final TeamCCallbackProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentHashMap<String, Delivery> deliveries = new ConcurrentHashMap<>();

    public TeamCCallbackClient(TeamCCallbackProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        validateConfiguration(properties);
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    @Override
    public void deliver(String applicationId, EligibilityDecisionResponse decision, String correlationId) {
        if (!properties.isEnabled()) return;
        if (applicationId == null || applicationId.isBlank() || decision == null
                || decision.getDecisionId() == null || decision.getDecisionId().isBlank()) {
            throw new IllegalArgumentException("Application and completed decision identifiers are required");
        }
        String leaseId = decision.getLeaseId();
        if (leaseId != null && leaseId.isBlank()) leaseId = null;
        TeamCEligibilityCallbackDto payload = new TeamCEligibilityCallbackDto(
                decision.getRequestId(), decision.getDecisionId(), decision.getEligibilityResult(),
                decision.getRuleSetVersion(), decision.getFailedRules(), leaseId);
        final String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize Team C eligibility callback", ex);
        }

        String decisionId = decision.getDecisionId();
        DeliveryStatus initialStatus = leaseId == null ? DeliveryStatus.BLOCKED_MISSING_LEASE : DeliveryStatus.PENDING;
        Delivery candidate = new Delivery(applicationId, decision.getRequestId(), decisionId,
                correlationId, body, initialStatus);
        Delivery existing = deliveries.putIfAbsent(decisionId, candidate);
        if (existing != null && (!existing.body().equals(body)
                || !existing.applicationId().equals(applicationId))) {
            throw new IllegalStateException("Callback payload or application changed for decision " + decisionId);
        }
        if (existing != null) return;
        if (initialStatus == DeliveryStatus.BLOCKED_MISSING_LEASE) {
            log.warn("Team C callback blocked: request_id={}, correlation_id={}, decision_id={}, callback_outcome=SKIPPED, reason=required_lease_unavailable",
                    decision.getRequestId(), correlationId, decisionId);
            return;
        }
        schedule(candidate, false);
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** Returns the current in-memory delivery state, or null if this decision has not been submitted. */
    public DeliveryStatus getDeliveryStatus(String decisionId) {
        Delivery delivery = deliveries.get(decisionId);
        return delivery == null ? null : delivery.getStatus();
    }

    public Integer getRemainingRedeliveries(String decisionId) {
        Delivery delivery = deliveries.get(decisionId);
        if (delivery == null) return null;
        synchronized (delivery) {
            return Math.max(0, properties.getMaxRedeliveries() - delivery.redeliveries());
        }
    }

    /**
     * Schedules one bounded manual redelivery round. This is enabled only after the receiver has
     * confirmed atomic Idempotency-Key handling for decision IDs. It reuses the exact body and key.
     */
    public RetryResult retryFailed(String decisionId) {
        if (!properties.isEnabled()) return RetryResult.CALLBACK_DISABLED;
        Delivery delivery = deliveries.get(decisionId);
        if (delivery == null) return RetryResult.NOT_FOUND;
        synchronized (delivery) {
            if (!delivery.isRetryableFailure()) return RetryResult.NOT_RETRYABLE;
            if (!properties.isReceiverIdempotencyConfirmed()) return RetryResult.RECEIVER_IDEMPOTENCY_UNCONFIRMED;
            if (delivery.redeliveries() >= properties.getMaxRedeliveries()) return RetryResult.RETRY_LIMIT_REACHED;
            delivery.incrementRedeliveries();
            delivery.setStatus(DeliveryStatus.PENDING);
        }
        return schedule(delivery, true) ? RetryResult.SCHEDULED : RetryResult.SCHEDULING_FAILED;
    }

    private boolean schedule(Delivery delivery, boolean redelivery) {
        try {
            executor.submit(() -> sendWithRetry(delivery));
            return true;
        } catch (RuntimeException ex) {
            delivery.setStatus(DeliveryStatus.FAILED_TO_SCHEDULE);
            log.warn("Team C callback could not be scheduled: request_id={}, correlation_id={}, decision_id={}, callback_outcome=FAILED, redelivery={}, error_type={}",
                    delivery.requestId(), delivery.correlationId(), delivery.decisionId(), redelivery,
                    ex.getClass().getSimpleName());
            return false;
        }
    }

    private void sendWithRetry(Delivery delivery) {
        URI uri = callbackUri(delivery.applicationId());
        int attemptLimit = properties.isReceiverIdempotencyConfirmed() ? properties.getMaxAttempts() : 1;
        for (int attempt = 1; attempt <= attemptLimit; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(properties.getReadTimeout())
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", delivery.decisionId())
                        .POST(HttpRequest.BodyPublishers.ofString(delivery.body()));
                String token = properties.getToken();
                if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
                if (delivery.correlationId() != null && !delivery.correlationId().isBlank()) {
                    builder.header("X-Correlation-ID", delivery.correlationId());
                }
                HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    delivery.setStatus(DeliveryStatus.DELIVERED);
                    log.info("Team C callback delivered: request_id={}, correlation_id={}, decision_id={}, callback_outcome=DELIVERED, status={}",
                            delivery.requestId(), delivery.correlationId(), delivery.decisionId(), status);
                    return;
                }
                if (status < 500 || status > 599) {
                    delivery.setStatus(DeliveryStatus.REJECTED_PERMANENTLY);
                    log.warn("Team C callback rejected: request_id={}, correlation_id={}, decision_id={}, callback_outcome=FAILED, status={}, retryable=false",
                            delivery.requestId(), delivery.correlationId(), delivery.decisionId(), status);
                    return;
                }
                log.warn("Team C callback failed: request_id={}, correlation_id={}, decision_id={}, callback_outcome=RETRYING, status={}, attempt={}",
                        delivery.requestId(), delivery.correlationId(), delivery.decisionId(), status, attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                delivery.setStatus(DeliveryStatus.FAILED_INTERRUPTED);
                log.warn("Team C callback interrupted: request_id={}, correlation_id={}, decision_id={}, callback_outcome=FAILED, attempt={}",
                        delivery.requestId(), delivery.correlationId(), delivery.decisionId(), attempt);
                return;
            } catch (IOException ex) {
                log.warn("Team C callback transport failure: request_id={}, correlation_id={}, decision_id={}, callback_outcome=RETRYING, error_type={}, attempt={}",
                        delivery.requestId(), delivery.correlationId(), delivery.decisionId(),
                        ex.getClass().getSimpleName(), attempt);
            } catch (RuntimeException ex) {
                delivery.setStatus(DeliveryStatus.FAILED_PERMANENTLY);
                log.warn("Team C callback failure: request_id={}, correlation_id={}, decision_id={}, callback_outcome=FAILED, error_type={}, attempt={}",
                        delivery.requestId(), delivery.correlationId(), delivery.decisionId(),
                        ex.getClass().getSimpleName(), attempt);
                return;
            }
            if (attempt < attemptLimit) pauseBeforeRetry();
        }
        if (!properties.isReceiverIdempotencyConfirmed()) {
            delivery.setStatus(DeliveryStatus.FAILED_REQUIRES_IDEMPOTENCY_CONFIRMATION);
            log.error("Team C callback not retried: request_id={}, correlation_id={}, decision_id={}, reason=receiver_idempotency_not_confirmed",
                    delivery.requestId(), delivery.correlationId(), delivery.decisionId());
        } else {
            delivery.setStatus(DeliveryStatus.FAILED_RETRIES_EXHAUSTED);
            log.error("Team C callback delivery exhausted retries: request_id={}, correlation_id={}, decision_id={}, callback_outcome=FAILED, reason=retries_exhausted",
                    delivery.requestId(), delivery.correlationId(), delivery.decisionId());
        }
    }

    private void pauseBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(properties.getRetryBackoff().toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private URI callbackUri(String applicationId) {
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String encodedId = URLEncoder.encode(applicationId, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(base + "/internal/v1/applications/" + encodedId + "/eligibility");
    }

    private static void validateConfiguration(TeamCCallbackProperties properties) {
        if (properties.getMaxAttempts() < 1 || properties.getMaxAttempts() > 10) {
            throw new IllegalArgumentException("Team C callback max-attempts must be between 1 and 10");
        }
        if (properties.getMaxRedeliveries() < 0 || properties.getMaxRedeliveries() > 10) {
            throw new IllegalArgumentException("Team C callback max-redeliveries must be between 0 and 10");
        }
        if (!properties.isEnabled()) return;
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("TEAM_C_BASE_URL is required when Team C callback is enabled");
        }
        URI base = URI.create(properties.getBaseUrl());
        boolean secure = "https".equalsIgnoreCase(base.getScheme());
        boolean localTest = "http".equalsIgnoreCase(base.getScheme()) && base.getHost() != null
                && (base.getHost().equals("localhost") || base.getHost().equals("127.0.0.1"));
        if ((!secure && !localTest) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("Team C callback URL must be HTTPS (HTTP is allowed only for localhost tests)");
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public enum RetryResult {
        SCHEDULED,
        CALLBACK_DISABLED,
        RECEIVER_IDEMPOTENCY_UNCONFIRMED,
        NOT_FOUND,
        NOT_RETRYABLE,
        RETRY_LIMIT_REACHED,
        SCHEDULING_FAILED
    }

    public enum DeliveryStatus {
        PENDING,
        DELIVERED,
        BLOCKED_MISSING_LEASE,
        REJECTED_PERMANENTLY,
        FAILED_PERMANENTLY,
        FAILED_INTERRUPTED,
        FAILED_TO_SCHEDULE,
        FAILED_REQUIRES_IDEMPOTENCY_CONFIRMATION,
        FAILED_RETRIES_EXHAUSTED
    }

    private static final class Delivery {
        private final String applicationId;
        private final String requestId;
        private final String decisionId;
        private final String correlationId;
        private final String body;
        private volatile DeliveryStatus status;
        private int redeliveries;

        private Delivery(String applicationId, String requestId, String decisionId,
                         String correlationId, String body, DeliveryStatus status) {
            this.applicationId = applicationId;
            this.requestId = requestId;
            this.decisionId = decisionId;
            this.correlationId = correlationId;
            this.body = body;
            this.status = status;
        }

        private boolean isRetryableFailure() {
            return status == DeliveryStatus.FAILED_RETRIES_EXHAUSTED
                    || status == DeliveryStatus.FAILED_REQUIRES_IDEMPOTENCY_CONFIRMATION
                    || status == DeliveryStatus.FAILED_TO_SCHEDULE;
        }
        private void incrementRedeliveries() { redeliveries++; }
        private int redeliveries() { return redeliveries; }
        private String applicationId() { return applicationId; }
        private String requestId() { return requestId; }
        private String decisionId() { return decisionId; }
        private String correlationId() { return correlationId; }
        private String body() { return body; }
        private DeliveryStatus getStatus() { return status; }
        private void setStatus(DeliveryStatus status) { this.status = status; }
    }
}
