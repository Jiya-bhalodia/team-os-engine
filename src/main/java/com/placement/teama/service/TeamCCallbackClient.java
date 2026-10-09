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

/** Best-effort outbound callback client. Delivery records are intentionally in-memory only. */
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
        String key = decision.getDecisionId();
        DeliveryStatus initialStatus = decision.getLeaseId() == null || decision.getLeaseId().isBlank()
                ? DeliveryStatus.BLOCKED_MISSING_LEASE : DeliveryStatus.PENDING;
        Delivery candidate = new Delivery(body, initialStatus);
        Delivery existing = deliveries.putIfAbsent(key, candidate);
        if (existing != null && !existing.body().equals(body)) {
            throw new IllegalStateException("Callback payload changed for decision " + key);
        }
        if (existing == null) {
            if (initialStatus == DeliveryStatus.BLOCKED_MISSING_LEASE) {
                log.warn("Team C callback blocked: decision_id={}, reason=required_lease_unavailable", key);
                return;
            }
            try {
                executor.submit(() -> sendWithRetry(applicationId, key, correlationId, body, candidate));
            } catch (RuntimeException ex) {
                candidate.setStatus(DeliveryStatus.FAILED_TO_SCHEDULE);
                log.warn("Team C callback could not be scheduled: decision_id={}, error={}",
                        key, ex.getClass().getSimpleName());
            }
        }
    }

    /** Returns the current in-memory delivery state, or null if this decision has not been submitted. */
    public DeliveryStatus getDeliveryStatus(String decisionId) {
        Delivery delivery = deliveries.get(decisionId);
        return delivery == null ? null : delivery.getStatus();
    }

    private void sendWithRetry(String applicationId, String decisionId, String correlationId, String body,
                               Delivery delivery) {
        URI uri = callbackUri(applicationId);
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                        .timeout(properties.getReadTimeout())
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + properties.getToken())
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                if (correlationId != null && !correlationId.isBlank()) {
                    builder.header("X-Correlation-ID", correlationId);
                }
                HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    delivery.setStatus(DeliveryStatus.DELIVERED);
                    log.info("Team C callback delivered: decision_id={}, status={}", decisionId, status);
                    return;
                }
                if (status < 500 || status > 599) {
                    delivery.setStatus(DeliveryStatus.REJECTED_PERMANENTLY);
                    log.warn("Team C callback rejected: decision_id={}, status={}, retryable=false", decisionId, status);
                    return;
                }
                log.warn("Team C callback failed: decision_id={}, status={}, attempt={}", decisionId, status, attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                delivery.setStatus(DeliveryStatus.FAILED_INTERRUPTED);
                log.warn("Team C callback interrupted: decision_id={}, attempt={}", decisionId, attempt);
                return;
            } catch (IOException ex) {
                log.warn("Team C callback transport failure: decision_id={}, error={}, attempt={}",
                        decisionId, ex.getClass().getSimpleName(), attempt);
            } catch (RuntimeException ex) {
                delivery.setStatus(DeliveryStatus.FAILED_PERMANENTLY);
                log.warn("Team C callback failure: decision_id={}, error={}, attempt={}",
                        decisionId, ex.getClass().getSimpleName(), attempt);
                return;
            }
            if (attempt < properties.getMaxAttempts()) pauseBeforeRetry();
        }
        delivery.setStatus(DeliveryStatus.FAILED_RETRIES_EXHAUSTED);
        log.error("Team C callback delivery exhausted retries: decision_id={}", decisionId);
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
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            throw new IllegalArgumentException("TEAM_C_CALLBACK_TOKEN is required when Team C callback is enabled");
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public enum DeliveryStatus {
        PENDING,
        DELIVERED,
        BLOCKED_MISSING_LEASE,
        REJECTED_PERMANENTLY,
        FAILED_PERMANENTLY,
        FAILED_INTERRUPTED,
        FAILED_TO_SCHEDULE,
        FAILED_RETRIES_EXHAUSTED
    }

    private static final class Delivery {
        private final String body;
        private volatile DeliveryStatus status;

        private Delivery(String body, DeliveryStatus status) {
            this.body = body;
            this.status = status;
        }

        private String body() { return body; }
        private DeliveryStatus getStatus() { return status; }
        private void setStatus(DeliveryStatus status) { this.status = status; }
    }
}
