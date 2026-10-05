package com.placement.teama.security;

import com.placement.teama.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Enforces a configured shared service credential without hard-coding secrets. */
@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    private final String token; private final ObjectMapper objectMapper;
    public ServiceAuthenticationFilter(@Value("${teama.security.service-token:}") String token, ObjectMapper objectMapper) { this.token = token; this.objectMapper = objectMapper; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return token.isBlank() || request.getRequestURI().equals("/health") || request.getRequestURI().equals("/ready"); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String auth = request.getHeader("Authorization");
        if (("Bearer " + token).equals(auth)) { chain.doFilter(request, response); return; }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error("UNAUTHORIZED", "A valid Authorization header is required", request.getHeader("X-Correlation-ID")));
    }
}
