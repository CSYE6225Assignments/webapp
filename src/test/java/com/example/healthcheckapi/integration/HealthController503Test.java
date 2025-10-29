package com.example.healthcheckapi.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Point to non-existent database to trigger real connection failure
        "spring.datasource.url=jdbc:mysql://localhost:9999/testdb?connectTimeout=500&socketTimeout=500",
        "spring.datasource.username=testuser",
        "spring.datasource.password=testpass",

        // Critical: Allow Spring to start even when database is unavailable
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.hikari.connection-timeout=500",
        "spring.datasource.hikari.validation-timeout=250",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.maximum-pool-size=1",

        // Disable JPA operations that require database
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults=false",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect",

        // Continue on error
        "spring.sql.init.continue-on-error=true",
        "spring.jpa.defer-datasource-initialization=true"
})
public class HealthController503Test {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testHealthCheck_Returns503_WhenDatabaseUnavailable() throws Exception {
        // Test that health check returns 503 when database is unreachable
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isServiceUnavailable())
                // Match actual Cache-Control header set by Spring Security
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(""));
    }
}