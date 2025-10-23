package com.example.healthcheckapi.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {
    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    @Value("${aws.s3.bucket-name:}")
    private String bucket;

    @Value("${aws.region:us-east-1}")
    private String region;

    private S3Client s3;

    @PostConstruct
    public void init() {
        if (bucket == null || bucket.isBlank()) {
            log.warn("S3 bucket name not configured. S3 operations will fail.");
            return;
        }
        s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        log.info("S3 client initialized for bucket: {} in region: {}", bucket, region);
    }

    @PreDestroy
    public void close() {
        if (s3 != null) {
            s3.close();
            log.info("S3 client closed");
        }
    }

    public String upload(MultipartFile file, Long userId, Long productId) throws IOException {
        if (s3 == null) {
            throw new IOException("S3 client not initialized");
        }

        String ext = "";
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            ext = name.substring(name.lastIndexOf("."));
        }
        String key = String.format("user_%d/product_%d/%s%s", userId, productId, UUID.randomUUID(), ext);

        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            s3.putObject(put, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("S3 upload successful: {}", key);
            return key;
        } catch (S3Exception e) {
            String errorMsg = e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage()
                    : e.getMessage();
            log.error("S3 upload failed: {}", errorMsg);
            throw new IOException("S3 upload failed", e);
        }
    }

    public void delete(String key) throws IOException {
        if (s3 == null) {
            throw new IOException("S3 client not initialized");
        }

        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("S3 delete successful: {}", key);
        } catch (S3Exception e) {
            String errorMsg = e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage()
                    : e.getMessage();
            log.error("S3 delete failed: {}", errorMsg);
            throw new IOException("S3 delete failed", e);
        }
    }
}