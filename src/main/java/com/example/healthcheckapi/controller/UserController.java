package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.UserService;
import jakarta.validation.Valid;
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

    @Autowired
    private UserService userService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
        // Check if user already exists
        if (userService.existsByUsername(user.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Create user (password will be encrypted in service)
        User createdUser = userService.createUser(user);

        // Return 201 with Location header
        return ResponseEntity
                .created(URI.create("/v1/user/" + createdUser.getId()))
                .body(createdUser);
    }

    @GetMapping(value = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUser(@PathVariable Long userId, Authentication auth) {
        User user = userService.findById(userId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Users can only get their own information
        if (!user.getUsername().equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(user);
    }

    @PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateUser(@PathVariable Long userId,
                                        @RequestBody Map<String, Object> updates,
                                        Authentication auth) {
        User user = userService.findById(userId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Users can only update their own information
        if (!user.getUsername().equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Validate that only allowed fields are being updated
        for (String key : updates.keySet()) {
            if (!key.equals("first_name") && !key.equals("last_name") && !key.equals("password")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }

        // Validate password length if provided
        String newPassword = null;
        if (updates.containsKey("password")) {
            newPassword = (String) updates.get("password");
            if (newPassword.length() < 8) {
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
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}