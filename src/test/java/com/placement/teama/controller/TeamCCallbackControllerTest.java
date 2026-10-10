package com.placement.teama.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.placement.teama.service.TeamCCallbackClient;
import com.placement.teama.service.TeamCCallbackProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamCCallbackControllerTest {
    @Test
    void schedulesConfirmedRetryWithAcceptedStatus() {
        TeamCCallbackClient client = clientReturning(TeamCCallbackClient.RetryResult.SCHEDULED);

        var response = new TeamCCallbackController(client).retry("DEC-1", "corr-1");

        assertEquals(202, response.getStatusCode().value());
        assertEquals("DEC-1", response.getBody().getData().get("decision_id"));
        assertEquals("PENDING", response.getBody().getData().get("delivery_status"));
    }

    @Test
    void refusesRetryWhenReceiverIdempotencyIsUnconfirmed() {
        TeamCCallbackClient client = clientReturning(TeamCCallbackClient.RetryResult.RECEIVER_IDEMPOTENCY_UNCONFIRMED);

        var response = new TeamCCallbackController(client).retry("DEC-1", null);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("CALLBACK_RETRY_RECEIVER_IDEMPOTENCY_UNCONFIRMED",
                response.getBody().getError().getCode());
    }

    @Test
    void exposesOnlyDeliveryStatusAndRemainingRetryBudget() {
        TeamCCallbackClient client = new TeamCCallbackClient(new TeamCCallbackProperties(), new ObjectMapper()) {
            @Override public DeliveryStatus getDeliveryStatus(String decisionId) {
                return DeliveryStatus.FAILED_RETRIES_EXHAUSTED;
            }
            @Override public Integer getRemainingRedeliveries(String decisionId) { return 1; }
        };

        var response = new TeamCCallbackController(client).status("DEC-1", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("FAILED_RETRIES_EXHAUSTED", response.getBody().getData().get("delivery_status"));
        assertEquals(1, response.getBody().getData().get("redeliveries_remaining"));
        assertEquals(3, response.getBody().getData().size());
    }

    private TeamCCallbackClient clientReturning(TeamCCallbackClient.RetryResult result) {
        return new TeamCCallbackClient(new TeamCCallbackProperties(), new ObjectMapper()) {
            @Override public RetryResult retryFailed(String decisionId) { return result; }
        };
    }
}
