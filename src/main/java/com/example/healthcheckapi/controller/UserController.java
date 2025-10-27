package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Timed(value = "api.user.create", description = "Create user endpoint")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
        logger.info("Creating user with username: {}", user.getUsername());

        try {
            // Check if user already exists
            if (userService.existsByUsername(user.getUsername())) {
                logger.warn("User creation failed: Username '{}' already exists", user.getUsername());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Create user (password will be encrypted in service)
            User createdUser = userService.createUser(user);
            logger.info("User created successfully: username={}, id={}",
                    createdUser.getUsername(), createdUser.getId());

            // Return 201 with Location header
            return ResponseEntity
                    .created(URI.create("/v1/user/" + createdUser.getId()))
                    .body(createdUser);

        } catch (Exception e) {
            logger.error("Error creating user '{}': {}", user.getUsername(), e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.user.get", description = "Get user endpoint")
    @GetMapping(value = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUser(@PathVariable Long userId, Authentication auth) {
        logger.info("Getting user: userId={}, requestedBy={}", userId, auth.getName());

        try {
            User user = userService.findById(userId);
            if (user == null) {
                logger.warn("User not found: userId={}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Users can only get their own information
            if (!user.getUsername().equals(auth.getName())) {
                logger.warn("Forbidden access: user '{}' tried to access userId={}",
                        auth.getName(), userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            logger.info("User retrieved successfully: userId={}", userId);
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            logger.error("Error retrieving user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.user.update", description = "Update user endpoint")
    @PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateUser(@PathVariable Long userId,
                                        @RequestBody Map<String, Object> updates,
                                        Authentication auth) {
        logger.info("Updating user: userId={}, requestedBy={}, fields={}",
                userId, auth.getName(), updates.keySet());

        try {
            User user = userService.findById(userId);
            if (user == null) {
                logger.warn("User update failed: userId={} not found", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Users can only update their own information
            if (!user.getUsername().equals(auth.getName())) {
                logger.warn("Forbidden update: user '{}' tried to update userId={}",
                        auth.getName(), userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Validate that only allowed fields are being updated
            for (String key : updates.keySet()) {
                if (!key.equals("first_name") && !key.equals("last_name") && !key.equals("password")) {
                    logger.warn("Invalid field in update request: {}", key);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }

            // Validate password length if provided
            String newPassword = null;
            if (updates.containsKey("password")) {
                newPassword = (String) updates.get("password");
                if (newPassword.length() < 8) {
                    logger.warn("Password validation failed: length < 8");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }

            // Update allowed fields
            if (updates.containsKey("first_name")) {
                user.setFirstName((String) updates.get("first_name"));
            }
            if (updates.containsKey("last_name")) {
                user.setLastName((String) updates.get("last_name"));
            }

            // Update user (password encryption handled in service if provided)
            userService.updateUser(user, newPassword);
            logger.info("User updated successfully: userId={}", userId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            logger.error("Error updating user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}