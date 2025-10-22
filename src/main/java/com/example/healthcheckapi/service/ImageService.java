package com.example.healthcheckapi.service;

import com.example.healthcheckapi.entity.Image;
import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private S3Service s3Service;

    @Autowired(required = false)
    private LocalStorageService localStorageService;

    @Value("${storage.type:s3}")
    private String storageType;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png"
    );

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

    public Image uploadImage(MultipartFile file, Product product, Long userId) throws IOException {
        String path = "local".equalsIgnoreCase(storageType) && localStorageService != null
                ? localStorageService.storeFile(file, userId, product.getId())
                : s3Service.upload(file, userId, product.getId());

        Image img = new Image();
        img.setFileName(file.getOriginalFilename());
        img.setS3BucketPath(path);
        img.setProduct(product);
        return imageRepository.save(img);
    }

    public List<Image> getImagesByProductId(Long productId) {
        return imageRepository.findByProduct_Id(productId);
    }

    public Image getImageByIdAndProductId(Long imageId, Long productId) {
        return imageRepository.findByImageIdAndProduct_Id(imageId, productId).orElse(null);
    }

    public void deleteImage(Image image) throws IOException {
        if ("local".equalsIgnoreCase(storageType) && localStorageService != null) {
            localStorageService.deleteFile(image.getS3BucketPath());
        } else {
            s3Service.delete(image.getS3BucketPath());
        }
        imageRepository.delete(image);
    }
}