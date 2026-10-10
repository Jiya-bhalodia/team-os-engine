package com.placement.teama.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "teama.team-c.callback")
public class TeamCCallbackProperties {
    private boolean enabled;
    private String baseUrl = "";
    private String token = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private int maxAttempts = 3;
    private Duration retryBackoff = Duration.ofMillis(250);
    private boolean receiverIdempotencyConfirmed;
    private int maxRedeliveries = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getRetryBackoff() { return retryBackoff; }
    public void setRetryBackoff(Duration retryBackoff) { this.retryBackoff = retryBackoff; }
    public boolean isReceiverIdempotencyConfirmed() { return receiverIdempotencyConfirmed; }
    public void setReceiverIdempotencyConfirmed(boolean receiverIdempotencyConfirmed) {
        this.receiverIdempotencyConfirmed = receiverIdempotencyConfirmed;
    }
    public int getMaxRedeliveries() { return maxRedeliveries; }
    public void setMaxRedeliveries(int maxRedeliveries) { this.maxRedeliveries = maxRedeliveries; }
}
