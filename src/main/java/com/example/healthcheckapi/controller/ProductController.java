package com.example.healthcheckapi.controller;

import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.service.ProductService;
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
    private ProductService productService;

    @Autowired
    private UserService userService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createProduct(@Valid @RequestBody Product product, Authentication auth) {
        if (productService.existsBySku(product.getSku())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        User user = userService.findByUsername(auth.getName());
        Product savedProduct = productService.createProduct(product, user);

        return ResponseEntity
                .created(URI.create("/v1/product/" + savedProduct.getId()))
                .body(savedProduct);
    }

    @GetMapping(value = "/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProduct(@PathVariable Long productId) {
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(product);
    }

    @PutMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPut(@PathVariable Long productId,
                                              @Valid @RequestBody Product updatedProduct,
                                              Authentication auth) {
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!productService.isOwner(product, auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!product.getSku().equals(updatedProduct.getSku()) &&
                productService.existsBySku(updatedProduct.getSku())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        product.setName(updatedProduct.getName());
        product.setDescription(updatedProduct.getDescription());
        product.setSku(updatedProduct.getSku());
        product.setManufacturer(updatedProduct.getManufacturer());
        product.setQuantity(updatedProduct.getQuantity());

        productService.updateProduct(product);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping(value = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateProductPatch(@PathVariable Long productId,
                                                @RequestBody Product updatedProduct,
                                                Authentication auth) {
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!productService.isOwner(product, auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Validate quantity if provided
        if (updatedProduct.getQuantity() != null) {
            if (updatedProduct.getQuantity() < 0 || updatedProduct.getQuantity() > 100) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }

        // Check if SKU is being changed to an existing one
        if (updatedProduct.getSku() != null &&
                !product.getSku().equals(updatedProduct.getSku()) &&
                productService.existsBySku(updatedProduct.getSku())) {
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
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long productId, Authentication auth) {
        Product product = productService.findById(productId);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (!productService.isOwner(product, auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        productService.deleteProduct(product);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}