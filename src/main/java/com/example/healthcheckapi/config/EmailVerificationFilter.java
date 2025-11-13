package com.example.healthcheckapi.config;

import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class EmailVerificationFilter extends OncePerRequestFilter {

    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip filter for public endpoints
        if (shouldSkipFilter(path, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // If authenticated, check email verification
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            User user = userService.findByUsername(auth.getName());

            if (user != null && !user.isEmailVerified()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Email not verified\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldSkipFilter(String path, String method) {
        // Allow POST to create user
        if (path.equals("/v1/user") && method.equals("POST")) {
            return true;
        }

        // Allow verification endpoint
        if (path.equals("/v1/user/verify")) {
            return true;
        }

        // Allow healthz
        if (path.startsWith("/healthz")) {
            return true;
        }

        // Allow GET requests to public product endpoints
        if (method.equals("GET") && path.startsWith("/v1/product")) {
            return true;
        }

        return false;
    }
}