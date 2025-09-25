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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/healthz", "/healthz/").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/user").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/product/*").permitAll()
                        // Authenticated endpoints
                        .requestMatchers("/v1/user/*").authenticated()
                        .requestMatchers("/v1/product", "/v1/product/*").authenticated()
                        .anyRequest().denyAll()
                )
                .httpBasic();

        return http.build();
    }
}