package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.repository.ProductRepository;
import com.example.healthcheckapi.service.UserService;
import jakarta.validation.Valid;
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

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserService userService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createProduct(@Valid @RequestBody Product product, Authentication auth) {
        // Check for duplicate SKU
        if (productRepository.existsBySku(product.getSku())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Set the owner to the authenticated user
        User user = userService.findByUsername(auth.getName());
        product.setOwner(user);

        Product savedProduct = productRepository.save(product);

        // Return 201 with Location header
        return ResponseEntity
                .created(URI.create("/v1/product/" + savedProduct.getId()))
                .body(savedProduct);
    }

    @GetMapping(value = "/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProduct(@PathVariable Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(product);
    }

    @PutMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPut(@PathVariable Long productId,
                                              @Valid @RequestBody Product updatedProduct,
                                              Authentication auth) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Only owner can update
        if (!product.getOwner().getUsername().equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Check if SKU is being changed to an existing one
        if (!product.getSku().equals(updatedProduct.getSku()) &&
                productRepository.existsBySku(updatedProduct.getSku())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // PUT is full update - all fields must be provided (validation handled by @Valid)
        product.setName(updatedProduct.getName());
        product.setDescription(updatedProduct.getDescription());
        product.setSku(updatedProduct.getSku());
        product.setManufacturer(updatedProduct.getManufacturer());
        product.setQuantity(updatedProduct.getQuantity());

        productRepository.save(product);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPatch(@PathVariable Long productId,
                                                @RequestBody Product updatedProduct,
                                                Authentication auth) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Only owner can update
        if (!product.getOwner().getUsername().equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Validate quantity if provided (minimum 0 and maximum 100)
        if (updatedProduct.getQuantity() != null) {
            if (updatedProduct.getQuantity() < 0 || updatedProduct.getQuantity() > 100) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }

        // Check if SKU is being changed to an existing one
        if (updatedProduct.getSku() != null &&
                !product.getSku().equals(updatedProduct.getSku()) &&
                productRepository.existsBySku(updatedProduct.getSku())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // PATCH - partial update, only update provided fields
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

        productRepository.save(product);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long productId, Authentication auth) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Only owner can delete
        if (!product.getOwner().getUsername().equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        productRepository.delete(product);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}