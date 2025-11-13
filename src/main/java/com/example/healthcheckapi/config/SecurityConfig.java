package com.example.healthcheckapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    private EmailVerificationFilter emailVerificationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(emailVerificationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no authentication required
                        .requestMatchers("/healthz", "/healthz/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/user").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/user/verify").permitAll()

                        // Public GET endpoints
                        .requestMatchers(HttpMethod.GET, "/v1/product/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/product/*/image").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/product/*/image/*").permitAll()

                        // User endpoints - must be authenticated
                        .requestMatchers("/v1/user/*").authenticated()

                        // Product mutations - must be authenticated
                        .requestMatchers(HttpMethod.POST, "/v1/product").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/v1/product/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/v1/product/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/product/*").authenticated()

                        // Image mutations - must be authenticated
                        .requestMatchers(HttpMethod.POST, "/v1/product/*/image").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/product/*/image/*").authenticated()

                        .anyRequest().denyAll()
                )
                .httpBasic();

        return http.build();
    }
}