package com.example.healthcheckapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "Name is required")
    private String name;

    @Column(nullable = false)
    @NotBlank(message = "Description is required")
    private String description;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "SKU is required")
    private String sku;

    @Column(nullable = false)
    @NotBlank(message = "Manufacturer is required")
    private String manufacturer;

    @Column(nullable = false)
    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be less than 0")
    @Max(value = 100, message = "Quantity cannot be more than 100")
    private Integer quantity;

    @Column(name = "date_added", nullable = false, updatable = false)
    @JsonProperty(value = "date_added", access = JsonProperty.Access.READ_ONLY)
    private Instant dateAdded;

    @Column(name = "date_last_updated", nullable = false)
    @JsonProperty(value = "date_last_updated", access = JsonProperty.Access.READ_ONLY)
    private Instant dateLastUpdated;

    @ManyToOne(fetch = FetchType.EAGER)  // Add EAGER fetching
    @JoinColumn(name = "owner_user_id", nullable = false)
    @JsonIgnore
    private User owner;

    @JsonProperty(value = "owner_user_id", access = JsonProperty.Access.READ_ONLY)
    public Long getOwnerId() {
        return owner != null ? owner.getId() : null;
    }

    @PrePersist
    protected void onCreate() {
        dateAdded = Instant.now();
        dateLastUpdated = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateLastUpdated = Instant.now();
    }

    // Setters for read-only fields that ignore client input
    public void setId(Long id) {
    }

    public void setDateAdded(Instant dateAdded) {
    }

    public void setDateLastUpdated(Instant dateLastUpdated) {
    }

    // getters and setters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Instant getDateAdded() {
        return dateAdded;
    }

    public Instant getDateLastUpdated() {
        return dateLastUpdated;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }
}
