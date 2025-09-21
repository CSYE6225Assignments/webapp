package com.example.healthcheckapi.entity;
import jakarta.persistence.*;

import java.time.Instant;


@Entity
@Table(name = "health_checks", indexes = {
        @Index(name = "idx_check_datetime", columnList = "check_datetime")
})
public class HealthCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id")
    private Long checkId;

    @Column(name = "check_datetime", nullable = false, updatable = false)
    private Instant checkDatetime;  // ← Change to Instant

    // Constructor - Sets UTC time automatically
    public HealthCheck() {
        this.checkDatetime = Instant.now();  // ← Always UTC
    }

    // Called before inserting to ensure UTC
    @PrePersist
    public void prePersist() {
        if (checkDatetime == null) {
            checkDatetime = Instant.now();  // ← Always UTC
        }
    }

    // Getters and Setters
    public Long getCheckId() {
        return checkId;
    }

    public void setCheckId(Long checkId) {
        this.checkId = checkId;
    }

    public Instant getCheckDatetime() {
        return checkDatetime;
    }

    public void setCheckDatetime(Instant checkDatetime) {
        this.checkDatetime = checkDatetime;
    }
}