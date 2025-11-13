package com.example.healthcheckapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;

@Service
public class SNSService {
    private static final Logger log = LoggerFactory.getLogger(SNSService.class);

    @Value("${aws.sns.topic-arn:}")
    private String topicArn;

    @Value("${aws.region}")
    private String region;

    private SnsClient snsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (topicArn == null || topicArn.isBlank()) {
            log.warn("SNS topic ARN not configured. SNS operations will fail.");
            return;
        }
        snsClient = SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        log.info("SNS client initialized for topic: {}", topicArn);
    }

    @PreDestroy
    public void close() {
        if (snsClient != null) {
            snsClient.close();
            log.info("SNS client closed");
        }
    }

    public void publishUserVerificationMessage(String email, String token, String domain) {
        if (snsClient == null) {
            throw new RuntimeException("SNS client not initialized");
        }

        try {
            Map<String, String> message = new HashMap<>();
            message.put("email", email);
            message.put("token", token);
            message.put("verificationLink", String.format("http://%s/v1/user/verify?email=%s&token=%s",
                    domain, email, token));
            message.put("timestamp", String.valueOf(System.currentTimeMillis()));

            String messageJson = objectMapper.writeValueAsString(message);

            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(messageJson)
                    .subject("Email Verification Required")
                    .build();

            PublishResponse response = snsClient.publish(publishRequest);
            log.info("SNS message published: MessageId={}, Email={}",
                    response.messageId(), email);

        } catch (Exception e) {
            log.error("Failed to publish SNS message for email: {}", email, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}