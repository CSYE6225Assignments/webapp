package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.Image;
import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.service.ImageService;
import com.example.healthcheckapi.service.ProductService;
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

    @Autowired
    private ImageService imageService;

    @Autowired
    private ProductService productService;

    /**
     * Upload image to a product
     * POST /v1/product/{product_id}/image
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadImage(
            @PathVariable("product_id") Long productId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        // Check authentication
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check if product exists
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Check if user owns the product
        if (!productService.isOwner(product, auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Validate file
        if (!imageService.isValidImageFile(file)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            // Upload image
            Image savedImage = imageService.uploadImage(file, product, product.getOwner().getId());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .location(URI.create("/v1/product/" + productId + "/image/" + savedImage.getImageId()))
                    .body(savedImage);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all images for a product
     * GET /v1/product/{product_id}/image
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllImages(@PathVariable("product_id") Long productId) {

        // Check if product exists
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<Image> images = imageService.getImagesByProductId(productId);
        return ResponseEntity.ok(images);
    }

    /**
     * Get specific image details
     * GET /v1/product/{product_id}/image/{image_id}
     */
    @GetMapping(value = "/{image_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getImageById(
            @PathVariable("product_id") Long productId,
            @PathVariable("image_id") Long imageId) {

        // Check if product exists
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Get image
        Image image = imageService.getImageByIdAndProductId(imageId, productId);
        if (image == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(image);
    }

    /**
     * Delete an image
     * DELETE /v1/product/{product_id}/image/{image_id}
     */
    @DeleteMapping("/{image_id}")
    public ResponseEntity<?> deleteImage(
            @PathVariable("product_id") Long productId,
            @PathVariable("image_id") Long imageId,
            Authentication auth) {

        // Check authentication
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check if product exists
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Check if user owns the product
        if (!productService.isOwner(product, auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Get image
        Image image = imageService.getImageByIdAndProductId(imageId, productId);
        if (image == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            imageService.deleteImage(image);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}