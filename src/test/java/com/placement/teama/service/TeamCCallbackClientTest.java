package com.placement.teama.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placement.teama.dto.EligibilityDecisionResponse;
import com.placement.teama.model.enums.EligibilityResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TeamCCallbackClientTest {
    private HttpServer server;
    private TeamCCallbackClient client;

    @AfterEach
    void cleanup() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    @Test
    void postsAllSupportedResultsWithUnchangedMessagesAndRealLeaseIds() throws Exception {
        AtomicInteger received = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(3);
        AtomicReference<JsonNode> lastBody = new AtomicReference<>();
        java.util.concurrent.CopyOnWriteArrayList<JsonNode> bodies = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            assertEquals("/internal/v1/applications/APP%2001/eligibility", exchange.getRequestURI().getRawPath());
            assertEquals("Bearer callback-secret", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("corr-1", exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            JsonNode body = new ObjectMapper().readTree(exchange.getRequestBody());
            lastBody.set(body);
            bodies.add(body);
            received.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            latch.countDown();
        });
        client = new TeamCCallbackClient(properties(3, 500, 0), new ObjectMapper());

        EligibilityResult[] results = {EligibilityResult.ELIGIBLE, EligibilityResult.CONDITIONAL,
                EligibilityResult.NOT_ELIGIBLE};
        for (int i = 0; i < results.length; i++) {
            client.deliver("APP 01", decision("DEC-" + i, results[i], "LEASE-real-" + i), "corr-1");
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(3, received.get());
        assertEquals(java.util.Set.of("ELIGIBLE", "CONDITIONAL", "NOT_ELIGIBLE"),
                bodies.stream().map(body -> body.get("result").asText()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(java.util.Set.of("LEASE-real-0", "LEASE-real-1", "LEASE-real-2"),
                bodies.stream().map(body -> body.get("lease_id").asText()).collect(java.util.stream.Collectors.toSet()));
        JsonNode body = lastBody.get();
        assertEquals("REQ", body.get("request_id").asText());
        assertEquals("v1", body.get("rule_set_version").asText());
        assertEquals("CGPA 6.5 is below required threshold 7.0", body.get("failed_rules").get(0).asText());
        assertEquals("LEASE-real-2", body.get("lease_id").asText());
    }

    @Test
    void missingLeaseBlocksDeliveryAndRecordsSanitizedStatus() throws Exception {
        AtomicInteger received = new AtomicInteger();
        startServer(exchange -> {
            received.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        client = new TeamCCallbackClient(properties(2, 500, 0), new ObjectMapper());
        client.deliver("APP", decision("DEC-no-lease", EligibilityResult.NOT_ELIGIBLE, null), null);

        assertEquals(TeamCCallbackClient.DeliveryStatus.BLOCKED_MISSING_LEASE,
                client.getDeliveryStatus("DEC-no-lease"));
        Thread.sleep(100);
        assertEquals(0, received.get());
    }

    @Test
    void missingRequiredConfigurationFailsWhenEnabled() throws Exception {
        TeamCCallbackProperties missingToken = enabledProperties("https://team-c.example", "test-token");
        missingToken.setToken("");
        assertThrows(IllegalArgumentException.class, () -> new TeamCCallbackClient(missingToken, new ObjectMapper()));

        TeamCCallbackProperties missingBaseUrl = enabledProperties("", "test-token");
        missingBaseUrl.setBaseUrl("");
        assertThrows(IllegalArgumentException.class, () -> new TeamCCallbackClient(missingBaseUrl, new ObjectMapper()));
    }

    @Test
    void disabledFeatureDoesNotSendCallback() throws Exception {
        AtomicInteger received = new AtomicInteger();
        startServer(exchange -> {
            received.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        TeamCCallbackProperties disabled = properties(2, 500, 0);
        disabled.setEnabled(false);
        disabled.setBaseUrl("");
        disabled.setToken("");
        client = new TeamCCallbackClient(disabled, new ObjectMapper());
        client.deliver("APP", decision("DEC-disabled", EligibilityResult.ELIGIBLE, "LEASE-real"), null);
        Thread.sleep(100);
        assertEquals(0, received.get());
        assertNull(client.getDeliveryStatus("DEC-disabled"));
    }

    @Test
    void retriesTimeoutAndFiveHundredButDoesNotRetryFourHundred() throws Exception {
        AtomicInteger timeoutCount = new AtomicInteger();
        CountDownLatch timeoutLatch = new CountDownLatch(2);
        java.util.concurrent.CopyOnWriteArrayList<String> timeoutBodies = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            timeoutCount.incrementAndGet();
            timeoutBodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            timeoutLatch.countDown();
            try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            try { exchange.sendResponseHeaders(204, -1); } catch (IOException ignored) { }
            exchange.close();
        });
        client = new TeamCCallbackClient(properties(2, 75, 1), new ObjectMapper());
        client.deliver("APP", decision("DEC-timeout", EligibilityResult.ELIGIBLE, "LEASE-timeout"), null);
        assertTrue(timeoutLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(250);
        assertEquals(2, timeoutCount.get());
        assertEquals(1, timeoutBodies.stream().distinct().count());
        assertEquals(TeamCCallbackClient.DeliveryStatus.FAILED_RETRIES_EXHAUSTED,
                client.getDeliveryStatus("DEC-timeout"));
        client.close();
        client = null;
        server.stop(0);

        AtomicInteger serverErrors = new AtomicInteger();
        CountDownLatch serverErrorLatch = new CountDownLatch(2);
        java.util.concurrent.CopyOnWriteArrayList<String> serverErrorBodies = new java.util.concurrent.CopyOnWriteArrayList<>();
        startServer(exchange -> {
            serverErrors.incrementAndGet();
            serverErrorBodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            serverErrorLatch.countDown();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        client = new TeamCCallbackClient(properties(2, 500, 1), new ObjectMapper());
        client.deliver("APP", decision("DEC-503", EligibilityResult.ELIGIBLE, "LEASE-503"), null);
        assertTrue(serverErrorLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(2, serverErrors.get());
        assertEquals(1, serverErrorBodies.stream().distinct().count());
        client.close();
        client = null;
        server.stop(0);

        AtomicInteger clientErrors = new AtomicInteger();
        CountDownLatch clientErrorLatch = new CountDownLatch(1);
        startServer(exchange -> {
            clientErrors.incrementAndGet();
            clientErrorLatch.countDown();
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        client = new TeamCCallbackClient(properties(3, 500, 1), new ObjectMapper());
        client.deliver("APP", decision("DEC-400", EligibilityResult.NOT_ELIGIBLE, "LEASE-400"), null);
        assertTrue(clientErrorLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(1, clientErrors.get());
        assertEquals(TeamCCallbackClient.DeliveryStatus.REJECTED_PERMANENTLY,
                client.getDeliveryStatus("DEC-400"));
    }

    @Test
    void duplicateDecisionDeliveryCreatesOnlyOneHttpRequest() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        startServer(exchange -> {
            count.incrementAndGet();
            latch.countDown();
            try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        client = new TeamCCallbackClient(properties(2, 500, 0), new ObjectMapper());
        EligibilityDecisionResponse decision = decision("DEC-same", EligibilityResult.ELIGIBLE, "LEASE-same");
        client.deliver("APP", decision, "corr");
        client.deliver("APP", decision, "corr");
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        Thread.sleep(150);
        assertEquals(1, count.get());
        assertThrows(IllegalStateException.class, () -> client.deliver("APP",
                decision("DEC-same", EligibilityResult.NOT_ELIGIBLE, "LEASE-same"), "corr"));
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private TeamCCallbackProperties properties(int attempts, int timeoutMs, int backoffMs) {
        return enabledProperties("http://127.0.0.1:" + server.getAddress().getPort(), "callback-secret", attempts, timeoutMs, backoffMs);
    }

    private TeamCCallbackProperties enabledProperties(String baseUrl, String token) {
        return enabledProperties(baseUrl, token, 2, 500, 0);
    }

    private TeamCCallbackProperties enabledProperties(String baseUrl, String token, int attempts, int timeoutMs, int backoffMs) {
        TeamCCallbackProperties p = new TeamCCallbackProperties();
        p.setEnabled(true);
        p.setBaseUrl(baseUrl);
        p.setToken(token);
        p.setConnectTimeout(Duration.ofMillis(200));
        p.setReadTimeout(Duration.ofMillis(timeoutMs));
        p.setMaxAttempts(attempts);
        p.setRetryBackoff(Duration.ofMillis(backoffMs));
        return p;
    }

    private EligibilityDecisionResponse decision(String id, EligibilityResult result, String leaseId) {
        return EligibilityDecisionResponse.builder().decisionId(id).requestId("REQ").applicationId("APP")
                .correlationId("corr-1").leaseId(leaseId).eligibilityResult(result)
                .failedRules(List.of("CGPA 6.5 is below required threshold 7.0")).ruleSetVersion("v1")
                .decisionMetrics(new EligibilityDecisionResponse.DecisionMetrics(1.0, 1)).build();
    }

}
