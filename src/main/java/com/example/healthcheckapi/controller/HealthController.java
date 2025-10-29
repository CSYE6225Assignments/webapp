package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.service.HealthCheckService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private HealthCheckService healthCheckService;

    @Timed(value = "api.health.check", description = "Health check endpoint")
    @RequestMapping({"/healthz", "/healthz/"})
    public ResponseEntity<Void> handleHealthCheck(HttpServletRequest request) {

        try {
            // Only GET method is supported
            if (!HttpMethod.GET.matches(request.getMethod())) {
                logger.warn("Health check failed: Invalid method {}", request.getMethod());
                return buildResponse(HttpStatus.METHOD_NOT_ALLOWED);
            }

            // Reject any query parameters
            if (request.getQueryString() != null) {
                logger.warn("Health check failed: Query parameters not allowed");
                return buildResponse(HttpStatus.BAD_REQUEST);
            }

            // Reject requests with payload (body content or chunked transfer)
            if (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null) {
                logger.warn("Health check failed: Body content not allowed");
                return buildResponse(HttpStatus.BAD_REQUEST);
            }

            // Perform health check by inserting database record
            boolean isHealthy = healthCheckService.performHealthCheck();

            if (isHealthy) {
                logger.info("Health check passed");
                return buildResponse(HttpStatus.OK);
            } else {
                logger.error("Health check failed: Database not accessible");
                return buildResponse(HttpStatus.SERVICE_UNAVAILABLE);
            }

        } catch (Exception e) {
            logger.error("Health check error: {}", e.getMessage(), e);
            return buildResponse(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private ResponseEntity<Void> buildResponse(HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();

        // Prevent caching of health status
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add("X-Content-Type-Options", "nosniff");

        // RFC 7231: 405 responses must include Allow header
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            headers.add(HttpHeaders.ALLOW, "GET");
        }

        return ResponseEntity.status(status).headers(headers).build();
    }
}