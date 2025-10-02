package com.example.healthcheckapi.integration;

import com.example.healthcheckapi.repository.HealthCheckRepository;
import com.example.healthcheckapi.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class HealthControllerIntegrationTest extends BaseIntegrationTest{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthCheckRepository healthCheckRepository;

    // Positive Test Cases
    @Test
    public void testHealthCheckSuccess() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(""));
    }

    @Test
    public void testHealthCheckWithTrailingSlash() throws Exception {
        mockMvc.perform(get("/healthz/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(""));
    }

    // Negative Test Cases

    @Test
    public void testHealthCheckWithQueryParams() throws Exception {
        mockMvc.perform(get("/healthz?param=value"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    public void testHealthCheckWithPayload() throws Exception {
        mockMvc.perform(get("/healthz")
                        .content("{\"test\":\"data\"}")
                        .contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testHealthCheckWithWrongMethod_POST() throws Exception {
        mockMvc.perform(post("/healthz"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    public void testHealthCheckWithWrongMethod_PUT() throws Exception {
        mockMvc.perform(put("/healthz"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    public void testHealthCheckWithWrongMethod_DELETE() throws Exception {
        mockMvc.perform(delete("/healthz"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    public void testHealthCheckWithWrongMethod_PATCH() throws Exception {
        mockMvc.perform(patch("/healthz"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"));
    }

    // Edge Cases
    @Test
    public void testHealthCheckWithContentLength() throws Exception {
        // Note: Content-Length header without actual content may not trigger bad request
        // depending on how the controller checks for payload
        mockMvc.perform(get("/healthz")
                        .content("test")  // Add actual content
                        .header("Content-Length", "4"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testHealthCheckWithTransferEncoding() throws Exception {
        mockMvc.perform(get("/healthz")
                        .header("Transfer-Encoding", "chunked"))
                .andExpect(status().isBadRequest());
    }

    // Test for verifying database side-effect
    @Test
    public void testHealthCheck_InsertsRow() throws Exception {
        long before = healthCheckRepository.count();
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
        long after = healthCheckRepository.count();
        assertEquals(before + 1, after);
    }

    @Test
    public void testHealthCheckResponseTime() throws Exception {
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk());
        long endTime = System.currentTimeMillis();
        assertTrue((endTime - startTime) < 1000, "Request should complete within 1 second");
    }
}