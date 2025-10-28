package com.example.healthcheckapi.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private S3Service s3Service;

    @BeforeEach
    public void setup() {
        // Set required fields via reflection (since @Value annotations won't work in unit tests)
        ReflectionTestUtils.setField(s3Service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "region", "us-east-1");
        ReflectionTestUtils.setField(s3Service, "s3", s3Client);
    }

    @Test
    public void testUpload_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        // Mock S3 response
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Upload
        String result = s3Service.upload(file, 1L, 1L);

        // Verify
        assertNotNull(result);
        assertTrue(result.startsWith("user_1/product_1/"));
        assertTrue(result.endsWith(".jpg"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testUpload_DifferentExtensions() throws IOException {
        MockMultipartFile pngFile = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "png content".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String result = s3Service.upload(pngFile, 2L, 3L);

        assertNotNull(result);
        assertTrue(result.startsWith("user_2/product_3/"));
        assertTrue(result.endsWith(".png"));
    }

    @Test
    public void testUpload_FileWithoutExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "noextension",
                "image/jpeg",
                "content".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String result = s3Service.upload(file, 1L, 1L);

        assertNotNull(result);
        assertTrue(result.startsWith("user_1/product_1/"));
        assertFalse(result.contains("."));
    }

    @Test
    public void testUpload_S3Exception() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        // Mock S3 exception
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder()
                        .message("S3 error")
                        .build());

        // Verify exception is thrown
        assertThrows(IOException.class, () -> {
            s3Service.upload(file, 1L, 1L);
        });
    }

    @Test
    public void testDelete_Success() throws IOException {
        String key = "user_1/product_1/test.jpg";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        // Should not throw exception
        assertDoesNotThrow(() -> s3Service.delete(key));

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    public void testDelete_S3Exception() {
        String key = "user_1/product_1/test.jpg";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder()
                        .message("Delete failed")
                        .build());

        // Verify exception is thrown
        assertThrows(IOException.class, () -> {
            s3Service.delete(key);
        });
    }

    @Test
    public void testUpload_UniqueKeys() throws IOException {
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "same-name.jpg",
                "image/jpeg",
                "content 1".getBytes()
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "same-name.jpg",
                "image/jpeg",
                "content 2".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String key1 = s3Service.upload(file1, 1L, 1L);
        String key2 = s3Service.upload(file2, 1L, 1L);

        // Even with same filename, keys should be different (UUID)
        assertNotEquals(key1, key2);
    }

    @Test
    public void testUpload_CorrectPartitioning() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // Test different user and product combinations
        String key1 = s3Service.upload(file, 1L, 1L);
        String key2 = s3Service.upload(file, 2L, 1L);
        String key3 = s3Service.upload(file, 1L, 2L);

        assertTrue(key1.startsWith("user_1/product_1/"));
        assertTrue(key2.startsWith("user_2/product_1/"));
        assertTrue(key3.startsWith("user_1/product_2/"));
    }
}