package com.example.healthcheckapi.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Filter that:
 * 1. Adds request context to MDC (Mapped Diagnostic Context) for structured logging
 * 2. Records HTTP request metrics via Micrometer
 * 3. Logs incoming and completed requests
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final MeterRegistry registry;

    public RequestLoggingFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        long startTime = System.nanoTime();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        // Add context to MDC
        MDC.put("reqId", requestId);
        MDC.put("method", req.getMethod());
        MDC.put("path", req.getRequestURI());
        MDC.put("endpoint", req.getRequestURI());
        MDC.put("event", "http_request_start");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser"))
                ? auth.getName()
                : "anonymous";
        MDC.put("user", username);

        logger.info("Incoming request: {} {}", req.getMethod(), req.getRequestURI());

        try {
            chain.doFilter(req, res);
            MDC.put("event", "http_request_complete");

        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            MDC.put("status", String.valueOf(res.getStatus()));
            MDC.put("durMs", String.valueOf(durationMs));

            logger.info("Request completed: {} {} - Status: {} - Duration: {}ms",
                    req.getMethod(), req.getRequestURI(), res.getStatus(), durationMs);

            Timer.builder("http.request")
                    .tag("method", req.getMethod())
                    .tag("uri", req.getRequestURI())
                    .tag("status", String.valueOf(res.getStatus()))
                    .description("HTTP request timing")
                    .register(registry)
                    .record(durationMs, TimeUnit.MILLISECONDS);

            MDC.clear();
        }
    }
}