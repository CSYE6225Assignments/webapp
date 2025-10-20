package com.example.healthcheckapi.service;

import com.example.healthcheckapi.entity.Image;
import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class ImageService {

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private LocalStorageService localStorageService;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png"
    );

    /**
     * Validate file type
     */
    public boolean isValidImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        String extension = "";
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex > 0) {
            extension = filename.substring(lastDotIndex + 1).toLowerCase();
        }

        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /**
     * Upload image for a product
     */
    public Image uploadImage(MultipartFile file, Product product, Long userId) throws IOException {
        // Store file locally (or S3 in production)
        String storagePath = localStorageService.storeFile(file, userId, product.getId());

        // Create image entity
        Image image = new Image();
        image.setFileName(file.getOriginalFilename());
        image.setS3BucketPath(storagePath);
        image.setProduct(product);

        return imageRepository.save(image);
    }

    /**
     * Get all images for a product
     */
    public List<Image> getImagesByProductId(Long productId) {
        return imageRepository.findByProduct_Id(productId);
    }

    /**
     * Get specific image by ID and product ID
     */
    public Image getImageByIdAndProductId(Long imageId, Long productId) {
        return imageRepository.findByImageIdAndProduct_Id(imageId, productId).orElse(null);
    }

    /**
     * Delete image
     */
    public void deleteImage(Image image) throws IOException {
        // Delete from storage
        localStorageService.deleteFile(image.getS3BucketPath());

        // Delete from database
        imageRepository.delete(image);
    }
}