package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.ProductService;
import com.example.healthcheckapi.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${email.verification.enabled:true}")
    private boolean emailVerificationEnabled;

    private boolean isEmailVerified(Authentication auth) {
        if (!emailVerificationEnabled) {
            return true;
        }
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        User user = userService.findByUsername(auth.getName());
        return user != null && user.isEmailVerified();
    }

    @Timed(value = "api.product.create", description = "Create product endpoint")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createProduct(@Valid @RequestBody Product product, Authentication auth) {
        MDC.put("event", "product_create_start");
        logger.info("Creating product: SKU={}, requestedBy={}", product.getSku(), auth.getName());

        try {
            if (!isEmailVerified(auth)) {
                MDC.put("event", "product_create_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (productService.existsBySku(product.getSku())) {
                MDC.put("event", "product_create_duplicate_sku");
                logger.warn("Product creation failed: SKU '{}' already exists", product.getSku());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            User user = userService.findByUsername(auth.getName());
            Product savedProduct = productService.createProduct(product, user);

            MDC.put("event", "product_create_success");
            logger.info("Product created successfully: id={}, SKU={}",
                    savedProduct.getId(), savedProduct.getSku());

            return ResponseEntity
                    .created(URI.create("/v1/product/" + savedProduct.getId()))
                    .body(savedProduct);

        } catch (Exception e) {
            MDC.put("event", "product_create_error");
            logger.error("Error creating product: SKU={}, error={}", product.getSku(), e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.product.get", description = "Get product endpoint")
    @GetMapping(value = "/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProduct(@PathVariable Long productId) {
        MDC.put("event", "product_get_start");
        logger.info("Getting product: productId={}", productId);

        try {
            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "product_get_not_found");
                logger.warn("Product not found: productId={}", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            MDC.put("event", "product_get_success");
            logger.info("Product retrieved successfully: productId={}", productId);
            return ResponseEntity.ok(product);

        } catch (Exception e) {
            MDC.put("event", "product_get_error");
            logger.error("Error retrieving product {}: {}", productId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.product.update", description = "Update product endpoint")
    @PutMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPut(@PathVariable Long productId,
                                              @Valid @RequestBody Product updatedProduct,
                                              Authentication auth) {
        MDC.put("event", "product_update_start");
        logger.info("Updating product: productId={}, requestedBy={}", productId, auth.getName());

        try {
            if (!isEmailVerified(auth)) {
                MDC.put("event", "product_create_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "product_update_not_found");
                logger.warn("Product update failed: productId={} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                MDC.put("event", "product_update_forbidden");
                logger.warn("Forbidden: User '{}' attempted to update product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (!product.getSku().equals(updatedProduct.getSku()) &&
                    productService.existsBySku(updatedProduct.getSku())) {
                MDC.put("event", "product_update_duplicate_sku");
                logger.warn("SKU conflict: New SKU '{}' already exists", updatedProduct.getSku());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            product.setName(updatedProduct.getName());
            product.setDescription(updatedProduct.getDescription());
            product.setSku(updatedProduct.getSku());
            product.setManufacturer(updatedProduct.getManufacturer());
            product.setQuantity(updatedProduct.getQuantity());

            productService.updateProduct(product);
            MDC.put("event", "product_update_success");
            logger.info("Product updated successfully: productId={}", productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            MDC.put("event", "product_update_error");
            logger.error("Error updating product {}: {}", productId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.product.patch", description = "Patch product endpoint")
    @PatchMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPatch(@PathVariable Long productId,
                                                @RequestBody Product updatedProduct,
                                                Authentication auth) {
        MDC.put("event", "product_patch_start");
        logger.info("Patching product: productId={}, requestedBy={}", productId, auth.getName());

        try {
            if (!isEmailVerified(auth)) {
                MDC.put("event", "product_create_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "product_patch_not_found");
                logger.warn("Product patch failed: productId={} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                MDC.put("event", "product_patch_forbidden");
                logger.warn("Forbidden: User '{}' attempted to patch product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Validate quantity
            if (updatedProduct.getQuantity() != null) {
                if (updatedProduct.getQuantity() < 0 || updatedProduct.getQuantity() > 100) {
                    MDC.put("event", "product_patch_invalid_quantity");
                    logger.warn("Invalid quantity: {}", updatedProduct.getQuantity());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
                }
            }

            // Check SKU conflict
            if (updatedProduct.getSku() != null &&
                    !product.getSku().equals(updatedProduct.getSku()) &&
                    productService.existsBySku(updatedProduct.getSku())) {
                MDC.put("event", "product_patch_duplicate_sku");
                logger.warn("SKU conflict: New SKU '{}' already exists", updatedProduct.getSku());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Partial update
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
            MDC.put("event", "product_patch_success");
            logger.info("Product patched successfully: productId={}", productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            MDC.put("event", "product_patch_error");
            logger.error("Error patching product {}: {}", productId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }

    @Timed(value = "api.product.delete", description = "Delete product endpoint")
    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long productId, Authentication auth) {
        MDC.put("event", "product_delete_start");
        logger.info("Deleting product: productId={}, requestedBy={}", productId, auth.getName());

        try {
            if (!isEmailVerified(auth)) {
                MDC.put("event", "product_create_email_not_verified");
                logger.warn("Access denied: Email not verified for user '{}'", auth.getName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Product product = productService.findById(productId);
            if (product == null) {
                MDC.put("event", "product_delete_not_found");
                logger.warn("Product delete failed: productId={} not found", productId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!productService.isOwner(product, auth.getName())) {
                MDC.put("event", "product_delete_forbidden");
                logger.warn("Forbidden: User '{}' attempted to delete product {}",
                        auth.getName(), productId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            productService.deleteProduct(product);
            MDC.put("event", "product_delete_success");
            logger.info("Product deleted successfully: productId={}", productId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();

        } catch (Exception e) {
            MDC.put("event", "product_delete_error");
            logger.error("Error deleting product {}: {}", productId, e.getMessage(), e);
            throw e;
        } finally {
            MDC.remove("event");
        }
    }
}