package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.Image;
import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.ImageService;
import com.example.healthcheckapi.service.ProductService;
import com.example.healthcheckapi.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    @Autowired
    private UserService userService;

    private boolean isEmailVerified(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        User user = userService.findByUsername(auth.getName());
        return user != null && user.isEmailVerified();
    }

    @Timed(value = "api.image.upload", description = "Upload image endpoint")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadImage(
            @PathVariable("product_id") Long productId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        MDC.put("event", "image_upload_start");
        logger.info("Uploading image: productId={}, requestedBy={}, file={}, size={} bytes",
                productId, auth != null ? auth.getName() : "anonymous",
                file.getOriginalFilename(), file.getSize());

        try {
            if (auth == null || auth.getName() == null) {
                MDC.put("event", "image_upload_unauthorized");
                logger.warn("Unauthorized image upload attempt for product {}", productId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check email verification
            if (!isEmailVerified(auth)) {
                MDC.put("event", "image_upload_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "image_upload_product_not_found");
                logger.warn("Image upload failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                MDC.put("event", "image_upload_forbidden");
                logger.warn("Forbidden: User '{}' attempted to upload image to product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (!imageService.isValidImageFile(file)) {
                MDC.put("event", "image_upload_invalid_file");
                logger.warn("Invalid image file: {}", file.getOriginalFilename());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            Image savedImage = imageService.uploadImage(file, product, product.getOwner().getId());

            MDC.put("event", "image_upload_success");
            logger.info("Image uploaded successfully: imageId={}, productId={}, filename={}",
                    savedImage.getImageId(), productId, savedImage.getFileName());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .location(URI.create("/v1/product/" + productId + "/image/" + savedImage.getImageId()))
                    .body(savedImage);

        } catch (Exception e) {
            MDC.put("event", "image_upload_error");
            logger.error("Error uploading image for product {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.image.getAll", description = "Get all images endpoint")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllImages(@PathVariable("product_id") Long productId) {
        MDC.put("event", "image_get_all_start");
        logger.info("Getting all images for product: productId={}", productId);

        try {
            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "image_get_all_product_not_found");
                logger.warn("Get all images failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            List<Image> images = imageService.getImagesByProductId(productId);
            MDC.put("event", "image_get_all_success");
            logger.info("Retrieved {} images for product {}", images.size(), productId);

            return ResponseEntity.ok(images);

        } catch (Exception e) {
            MDC.put("event", "image_get_all_error");
            logger.error("Error retrieving images for product {}: {}", productId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.image.get", description = "Get image endpoint")
    @GetMapping(value = "/{image_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getImageById(
            @PathVariable("product_id") Long productId,
            @PathVariable("image_id") Long imageId) {

        MDC.put("event", "image_get_start");
        logger.info("Getting image: productId={}, imageId={}", productId, imageId);

        try {
            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "image_get_product_not_found");
                logger.warn("Get image failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Image image = imageService.getImageByIdAndProductId(imageId, productId);
            if (image == null) {
                MDC.put("event", "image_get_not_found");
                logger.warn("Image not found: imageId={}, productId={}", imageId, productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            MDC.put("event", "image_get_success");
            logger.info("Image retrieved: imageId={}, productId={}", imageId, productId);
            return ResponseEntity.ok(image);

        } catch (Exception e) {
            MDC.put("event", "image_get_error");
            logger.error("Error retrieving image {} for product {}: {}", imageId, productId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.image.delete", description = "Delete image endpoint")
    @DeleteMapping("/{image_id}")
    public ResponseEntity<?> deleteImage(
            @PathVariable("product_id") Long productId,
            @PathVariable("image_id") Long imageId,
            Authentication auth) {

        MDC.put("event", "image_delete_start");
        logger.info("Deleting image: productId={}, imageId={}, requestedBy={}",
                productId, imageId, auth != null ? auth.getName() : "anonymous");

        try {
            if (auth == null || auth.getName() == null) {
                MDC.put("event", "image_delete_unauthorized");
                logger.warn("Unauthorized image delete attempt: imageId={}, productId={}", imageId, productId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Check email verification
            if (!isEmailVerified(auth)) {
                MDC.put("event", "image_delete_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "image_delete_product_not_found");
                logger.warn("Image delete failed: Product {} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                MDC.put("event", "image_delete_forbidden");
                logger.warn("Forbidden: User '{}' attempted to delete image from product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Image image = imageService.getImageByIdAndProductId(imageId, productId);
            if (image == null) {
                MDC.put("event", "image_delete_not_found");
                logger.warn("Image delete failed: imageId={}, productId={} not found", imageId, productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            imageService.deleteImage(image);
            MDC.put("event", "image_delete_success");
            logger.info("Image deleted successfully: imageId={}, productId={}", imageId, productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            MDC.put("event", "image_delete_error");
            logger.error("Error deleting image {} for product {}: {}", imageId, productId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            MDC.remove("event");
        }
    }
}