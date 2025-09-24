package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

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
}