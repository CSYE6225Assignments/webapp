package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.ProductService;
import com.example.healthcheckapi.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/v1/product")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Timed(value = "api.product.create", description = "Create product endpoint")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createProduct(@Valid @RequestBody Product product, Authentication auth) {
        logger.info("Creating product: SKU={}, requestedBy={}", product.getSku(), auth.getName());

        try {
            if (productService.existsBySku(product.getSku())) {
                logger.warn("Product creation failed: SKU '{}' already exists", product.getSku());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            User user = userService.findByUsername(auth.getName());
            Product savedProduct = productService.createProduct(product, user);

            logger.info("Product created successfully: id={}, SKU={}",
                    savedProduct.getId(), savedProduct.getSku());

            return ResponseEntity
                    .created(URI.create("/v1/product/" + savedProduct.getId()))
                    .body(savedProduct);

        } catch (Exception e) {
            logger.error("Error creating product: SKU={}, error={}", product.getSku(), e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.product.get", description = "Get product endpoint")
    @GetMapping(value = "/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProduct(@PathVariable Long productId) {
        logger.info("Getting product: productId={}", productId);

        try {
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Product not found: productId={}", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            logger.info("Product retrieved successfully: productId={}", productId);
            return ResponseEntity.ok(product);

        } catch (Exception e) {
            logger.error("Error retrieving product {}: {}", productId, e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.product.update", description = "Update product endpoint")
    @PutMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPut(@PathVariable Long productId,
                                              @Valid @RequestBody Product updatedProduct,
                                              Authentication auth) {
        logger.info("Updating product: productId={}, requestedBy={}", productId, auth.getName());

        try {
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Product update failed: productId={} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                logger.warn("Forbidden: User '{}' attempted to update product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (!product.getSku().equals(updatedProduct.getSku()) &&
                    productService.existsBySku(updatedProduct.getSku())) {
                logger.warn("SKU conflict: New SKU '{}' already exists", updatedProduct.getSku());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            product.setName(updatedProduct.getName());
            product.setDescription(updatedProduct.getDescription());
            product.setSku(updatedProduct.getSku());
            product.setManufacturer(updatedProduct.getManufacturer());
            product.setQuantity(updatedProduct.getQuantity());

            productService.updateProduct(product);
            logger.info("Product updated successfully: productId={}", productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            logger.error("Error updating product {}: {}", productId, e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.product.patch", description = "Patch product endpoint")
    @PatchMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPatch(@PathVariable Long productId,
                                                @RequestBody Product updatedProduct,
                                                Authentication auth) {
        logger.info("Patching product: productId={}, requestedBy={}", productId, auth.getName());

        try {
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Product patch failed: productId={} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                logger.warn("Forbidden: User '{}' attempted to patch product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Validate quantity if provided
            if (updatedProduct.getQuantity() != null) {
                if (updatedProduct.getQuantity() < 0 || updatedProduct.getQuantity() > 100) {
                    logger.warn("Invalid quantity: {}", updatedProduct.getQuantity());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }

            // Check if SKU is being changed to an existing one
            if (updatedProduct.getSku() != null &&
                    !product.getSku().equals(updatedProduct.getSku()) &&
                    productService.existsBySku(updatedProduct.getSku())) {
                logger.warn("SKU conflict: New SKU '{}' already exists", updatedProduct.getSku());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // PATCH - partial update
            if (updatedProduct.getName() != null) {
                product.setName(updatedProduct.getName());
            }
            if (updatedProduct.getDescription() != null) {
                product.setDescription(updatedProduct.getDescription());
            }
            if (updatedProduct.getSku() != null) {
                product.setSku(updatedProduct.getSku());
            }
            if (updatedProduct.getManufacturer() != null) {
                product.setManufacturer(updatedProduct.getManufacturer());
            }
            if (updatedProduct.getQuantity() != null) {
                product.setQuantity(updatedProduct.getQuantity());
            }

            productService.updateProduct(product);
            logger.info("Product patched successfully: productId={}", productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            logger.error("Error patching product {}: {}", productId, e.getMessage(), e);
            throw e;
        }
    }

    @Timed(value = "api.product.delete", description = "Delete product endpoint")
    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long productId, Authentication auth) {
        logger.info("Deleting product: productId={}, requestedBy={}", productId, auth.getName());

        try {
            Product product = productService.findById(productId);
            if (product == null) {
                logger.warn("Product delete failed: productId={} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                logger.warn("Forbidden: User '{}' attempted to delete product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            productService.deleteProduct(product);
            logger.info("Product deleted successfully: productId={}", productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            logger.error("Error deleting product {}: {}", productId, e.getMessage(), e);
            throw e;
        }
    }
}