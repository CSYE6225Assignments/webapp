package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
        MDC.put("event", "user_create_start");
        logger.info("Creating user with username: {}", user.getUsername());

        try {
            if (userService.existsByUsername(user.getUsername())) {
                MDC.put("event", "user_create_duplicate");
                logger.warn("User creation failed: Username '{}' already exists", user.getUsername());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            User createdUser = userService.createUser(user);
            MDC.put("event", "user_create_success");
            logger.info("User created successfully: username={}, id={}",
                    createdUser.getUsername(), createdUser.getId());

            return ResponseEntity
                    .created(URI.create("/v1/user/" + createdUser.getId()))
                    .body(createdUser);

        } catch (Exception e) {
            MDC.put("event", "user_create_error");
            logger.error("Error creating user '{}': {}", user.getUsername(), e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.user.get", description = "Get user endpoint")
    @GetMapping(value = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUser(@PathVariable Long userId, Authentication auth) {
        MDC.put("event", "user_get_start");
        logger.info("Getting user: userId={}, requestedBy={}", userId, auth.getName());

        try {
            User user = userService.findById(userId);
            if (user == null) {
                MDC.put("event", "user_get_not_found");
                logger.warn("User not found: userId={}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!user.getUsername().equals(auth.getName())) {
                MDC.put("event", "user_get_forbidden");
                logger.warn("Forbidden access: user '{}' tried to access userId={}",
                        auth.getName(), userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            MDC.put("event", "user_get_success");
            logger.info("User retrieved successfully: userId={}", userId);
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            MDC.put("event", "user_get_error");
            logger.error("Error retrieving user {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.user.update", description = "Update user endpoint")
    @PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateUser(@PathVariable Long userId,
                                        @RequestBody Map<String, Object> updates,
                                        Authentication auth) {
        MDC.put("event", "user_update_start");
        logger.info("Updating user: userId={}, requestedBy={}, fields={}",
                userId, auth.getName(), updates.keySet());

        try {
            User user = userService.findById(userId);
            if (user == null) {
                MDC.put("event", "user_update_not_found");
                logger.warn("User update failed: userId={} not found", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!user.getUsername().equals(auth.getName())) {
                MDC.put("event", "user_update_forbidden");
                logger.warn("Forbidden update: user '{}' tried to update userId={}",
                        auth.getName(), userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Validate allowed fields
            for (String key : updates.keySet()) {
                if (!key.equals("first_name") && !key.equals("last_name") && !key.equals("password")) {
                    MDC.put("event", "user_update_invalid_field");
                    logger.warn("Invalid field in update request: {}", key);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }

            // Validate password
            String newPassword = null;
            if (updates.containsKey("password")) {
                newPassword = (String) updates.get("password");
                if (newPassword.length() < 8) {
                    MDC.put("event", "user_update_invalid_password");
                    logger.warn("Password validation failed: length < 8");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }

            // Update fields
            if (updates.containsKey("first_name")) {
                user.setFirstName((String) updates.get("first_name"));
            }
            if (updates.containsKey("last_name")) {
                user.setLastName((String) updates.get("last_name"));
            }

            userService.updateUser(user, newPassword);
            MDC.put("event", "user_update_success");
            logger.info("User updated successfully: userId={}", userId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            MDC.put("event", "user_update_error");
            logger.error("Error updating user {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }
}