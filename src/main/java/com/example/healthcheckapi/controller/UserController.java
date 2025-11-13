package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.EmailVerificationService;
import com.example.healthcheckapi.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
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
import com.example.healthcheckapi.entity.EmailVerificationToken;
import com.example.healthcheckapi.service.SNSService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private EmailVerificationService verificationService;

    @Autowired
    private SNSService snsService;

    @Value("${ENVIRONMENT:dev}")
    private String environment;

    @Value("${domain_name:dhruvbaraiya.me}")
    private String domainName;

    private String fullDomain;

    @PostConstruct
    private void init() {
        fullDomain = environment + "." + domainName;
    }

    private boolean isEmailVerified(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        User user = userService.findByUsername(auth.getName());
        return user != null && user.isEmailVerified();
    }

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

            // Check if verification email already sent
            if (verificationService.hasUnverifiedToken(user.getUsername())) {
                MDC.put("event", "user_create_verification_pending");
                logger.warn("User creation blocked: Verification email already sent for '{}'", user.getUsername());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            User createdUser = userService.createUser(user);

            // Create verification token
            EmailVerificationToken token = verificationService.createToken(createdUser.getUsername());

            // Publish to SNS
            try {
                snsService.publishUserVerificationMessage(
                        createdUser.getUsername(),
                        token.getToken(),
                        fullDomain
                );
                MDC.put("event", "user_verification_email_queued");
                logger.info("Verification email queued for user: {}", createdUser.getUsername());
            } catch (Exception e) {
                MDC.put("event", "user_verification_email_failed");
                logger.error("Failed to queue verification email for user: {}", createdUser.getUsername(), e);
                // Continue - user is created but email failed
            }

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
            // Check email verification
            if (!isEmailVerified(auth)) {
                MDC.put("event", "user_get_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

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
            // Check email verification
            if (!isEmailVerified(auth)) {
                MDC.put("event", "user_update_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

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

    @Timed(value = "api.user.verify", description = "Verify email endpoint")
    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(
            @RequestParam String email,
            @RequestParam String token) {

        MDC.put("event", "email_verification_start");
        logger.info("Email verification attempt: email={}, token={}", email, token);

        try {
            boolean verified = verificationService.verifyToken(email, token);

            if (verified) {
                MDC.put("event", "email_verification_success");
                logger.info("Email verified successfully: {}", email);
                return ResponseEntity.ok().build();
            } else {
                MDC.put("event", "email_verification_failed");
                logger.warn("Email verification failed: email={}, token={}", email, token);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

        } catch (Exception e) {
            MDC.put("event", "email_verification_error");
            logger.error("Error verifying email: {}", email, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } finally {
            MDC.remove("event");
        }
    }
}