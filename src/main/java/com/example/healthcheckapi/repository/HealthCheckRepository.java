package com.example.healthcheckapi.repository;

import com.example.healthcheckapi.entity.HealthCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {
    // No additional methods needed
}
