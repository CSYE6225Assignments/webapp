package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.service.HealthCheckService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {

    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * Health check endpoint - verifies database connectivity
     * Accepts: GET /healthz or /healthz/ without payload
     * Returns: 200 (healthy), 503 (unhealthy), 400 (bad request), 405 (method not allowed)
     */
    @RequestMapping({"/healthz", "/healthz/"})
    public ResponseEntity<Void> handleHealthCheck(HttpServletRequest request) {

        // Only GET method is supported
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return buildResponse(HttpStatus.METHOD_NOT_ALLOWED);
        }

        // Reject any query parameters
        if (request.getQueryString() != null) {
            return buildResponse(HttpStatus.BAD_REQUEST);
        }


        // Reject requests with payload (body content or chunked transfer)
        if (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null) {
            return buildResponse(HttpStatus.BAD_REQUEST);
        }

        // Perform health check by inserting database record
        boolean isHealthy = healthCheckService.performHealthCheck();

        return isHealthy
                ? buildResponse(HttpStatus.OK)
                : buildResponse(HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Builds response with required security and cache headers
     */
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