package com.example.healthcheckapi.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
            logger.info("Upload directory created/verified at: {}", uploadDir);
        } catch (IOException e) {
            logger.error("Could not create upload directory!", e);
        }
    }

    /**
     * Store file locally and return the storage path
     */
    public String storeFile(MultipartFile file, Long userId, Long productId) throws IOException {
        // Create directory structure: uploads/user_{userId}/product_{productId}/
        String userDir = "user_" + userId;
        String productDir = "product_" + productId;
        Path directoryPath = Paths.get(uploadDir, userDir, productDir);

        // Create directories if they don't exist
        Files.createDirectories(directoryPath);

        // Generate unique filename to avoid collisions
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

        // Full file path
        Path filePath = directoryPath.resolve(uniqueFilename);

        // Copy file to the target location
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("File stored at: {}", filePath.toString());

        // Return relative path
        return userDir + "/" + productDir + "/" + uniqueFilename;
    }

    /**
     * Delete file from local storage
     */
    public void deleteFile(String filePath) throws IOException {
        Path path = Paths.get(uploadDir, filePath);
        Files.deleteIfExists(path);
        logger.info("File deleted: {}", path.toString());
    }

    /**
     * Check if file exists
     */
    public boolean fileExists(String filePath) {
        Path path = Paths.get(uploadDir, filePath);
        return Files.exists(path);
    }
}