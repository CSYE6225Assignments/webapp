package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.Image;
import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.service.ImageService;
import com.example.healthcheckapi.service.ProductService;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/product/{product_id}/image")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    @Autowired
    private ImageService imageService;

    @Autowired
    private ProductService productService;

    @Timed(value = "api.image.upload", description = "Upload image endpoint")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadImage(
            @PathVariable("product_id") Long productId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        logger.info("Uploading image: productId={}, requestedBy={}, file={}, size={} bytes",
                productId, auth != null ? auth.getName() : "anonymous",
                file.getOriginalFilename(), file.getSize());

        try {
            // Check authentication
            if (auth == null || auth.getName() == null) {
                logger.warn("Unauthorized image upload attempt for product {}", productId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check if product exists
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Image upload failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Check if user owns the product
            if (!productService.isOwner(product, auth.getName())) {
                logger.warn("Forbidden: User '{}' attempted to upload image to product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Validate file
            if (!imageService.isValidImageFile(file)) {
                logger.warn("Invalid image file: {}", file.getOriginalFilename());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Upload image
            Image savedImage = imageService.uploadImage(file, product, product.getOwner().getId());

            logger.info("Image uploaded successfully: imageId={}, productId={}, filename={}",
                    savedImage.getImageId(), productId, savedImage.getFileName());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .location(URI.create("/v1/product/" + productId + "/image/" + savedImage.getImageId()))
                    .body(savedImage);

        } catch (Exception e) {
            logger.error("Error uploading image for product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Timed(value = "api.image.getAll", description = "Get all images endpoint")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllImages(@PathVariable("product_id") Long productId) {
        logger.info("Getting all images for product: productId={}", productId);

        try {
            // Check if product exists
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Get all images failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            List<Image> images = imageService.getImagesByProductId(productId);
            logger.info("Retrieved {} images for product {}", images.size(), productId);

            return ResponseEntity.ok(images);

        } catch (Exception e) {
            logger.error("Error retrieving images for product {}: {}", productId, e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.image.get", description = "Get image endpoint")
    @GetMapping(value = "/{image_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getImageById(
            @PathVariable("product_id") Long productId,
            @PathVariable("image_id") Long imageId) {

        logger.info("Getting image: productId={}, imageId={}", productId, imageId);

        try {
            // Check if product exists
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Get image failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Get image
            Image image = imageService.getImageByIdAndProductId(imageId, productId);
            if (image == null) {
                logger.warn("Image not found: imageId={}, productId={}", imageId, productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            logger.info("Image retrieved: imageId={}, productId={}", imageId, productId);
            return ResponseEntity.ok(image);

        } catch (Exception e) {
            logger.error("Error retrieving image {} for product {}: {}", imageId, productId, e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.image.delete", description = "Delete image endpoint")
    @DeleteMapping("/{image_id}")
    public ResponseEntity<?> deleteImage(
            @PathVariable("product_id") Long productId,
            @PathVariable("image_id") Long imageId,
            Authentication auth) {

        logger.info("Deleting image: productId={}, imageId={}, requestedBy={}",
                productId, imageId, auth != null ? auth.getName() : "anonymous");

        try {
            // Check authentication
            if (auth == null || auth.getName() == null) {
                logger.warn("Unauthorized image delete attempt: imageId={}, productId={}", imageId, productId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check if product exists
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Image delete failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Check if user owns the product
            if (!productService.isOwner(product, auth.getName())) {
                logger.warn("Forbidden: User '{}' attempted to delete image from product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Get image
            Image image = imageService.getImageByIdAndProductId(imageId, productId);
            if (image == null) {
                logger.warn("Image delete failed: imageId={}, productId={} not found", imageId, productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            imageService.deleteImage(image);
            logger.info("Image deleted successfully: imageId={}, productId={}", imageId, productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            logger.error("Error deleting image {} for product {}: {}", imageId, productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}