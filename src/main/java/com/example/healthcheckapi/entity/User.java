package com.example.healthcheckapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Username is required")
    @Email(message = "Username must be a valid email")
    private String username;

    @Column(nullable = false)
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "first_name", nullable = false)
    @NotBlank(message = "First name is required")
    @JsonProperty("first_name")
    private String firstName;

    @Column(name = "last_name", nullable = false)
    @NotBlank(message = "Last name is required")
    @JsonProperty("last_name")
    private String lastName;

    @Column(name = "account_created", nullable = false, updatable = false)
    @JsonProperty(value = "account_created", access = JsonProperty.Access.READ_ONLY)
    private Instant accountCreated;

    @Column(name = "account_updated", nullable = false)
    @JsonProperty(value = "account_updated", access = JsonProperty.Access.READ_ONLY)
    private Instant accountUpdated;

    @Column(name = "email_verified", nullable = false)
    @JsonProperty(value = "email_verified", access = JsonProperty.Access.READ_ONLY)
    private boolean emailVerified = false;

    @OneToMany(mappedBy = "owner")
    @JsonIgnore
    private List<Product> products;

    @PrePersist
    protected void onCreate() {
        accountCreated = Instant.now();
        accountUpdated = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        accountUpdated = Instant.now();
    }

    // Setters for read-only fields that ignore client input
    public void setId(Long id) {
    }

    public void setAccountCreated(Instant accountCreated) {
    }

    public void setAccountUpdated(Instant accountUpdated) {
    }

    // Normal getters and setters
    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Instant getAccountCreated() {
        return accountCreated;
    }

    public Instant getAccountUpdated() {
        return accountUpdated;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }
}
