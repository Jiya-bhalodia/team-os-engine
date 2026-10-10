package com.placement.teama.security;

import com.placement.teama.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/** Enforces a configured shared service credential without hard-coding secrets. */
@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ServiceAuthenticationFilter.class);
    private final String token; private final ObjectMapper objectMapper;
    public ServiceAuthenticationFilter(@Value("${teama.security.service-token:}") String token, ObjectMapper objectMapper) { this.token = token == null ? "" : token; this.objectMapper = objectMapper; }

    @PostConstruct
    void warnIfAuthenticationIsNotConfigured() {
        if (token.isBlank()) {
            log.warn("TEAM_A_SERVICE_TOKEN is not configured; /internal/v1/** endpoints will reject requests");
        }
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health") || path.equals("/ready")
                || (token.isBlank() && !path.startsWith("/internal/v1/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error("SERVICE_AUTH_NOT_CONFIGURED",
                    "Internal service authentication is not configured", request.getHeader("X-Correlation-ID")));
            return;
        }
        String auth = request.getHeader("Authorization");
        if (("Bearer " + token).equals(auth)) { chain.doFilter(request, response); return; }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error("UNAUTHORIZED", "A valid Authorization header is required", request.getHeader("X-Correlation-ID")));
    }
}
