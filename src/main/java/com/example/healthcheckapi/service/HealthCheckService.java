package com.example.healthcheckapi.service;

import com.example.healthcheckapi.entity.HealthCheck;
import com.example.healthcheckapi.repository.HealthCheckRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class HealthCheckService {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);

    @Autowired
    private HealthCheckRepository healthCheckRepository;

    /**
     * Verifies database connectivity by inserting a health check record
     * @return true if database is accessible, false otherwise
     */
    @Transactional
    public boolean performHealthCheck() {
        try {
            // saveAndFlush forces immediate DB write to verify connectivity
            healthCheckRepository.saveAndFlush(new HealthCheck());
            return true;
        } catch (Exception e) {
            logger.error("Health check failed", e);
            return false;
        }
    }
}