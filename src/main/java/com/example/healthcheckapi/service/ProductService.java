package com.example.healthcheckapi.service;

import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public Product createProduct(Product product, User owner) {
        product.setOwner(owner);
        return productRepository.save(product);
    }

    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public boolean existsBySku(String sku) {
        return productRepository.existsBySku(sku);
    }

    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }

    public void deleteProduct(Product product) {
        productRepository.delete(product);
    }

    public boolean isOwner(Product product, String username) {
        return product.getOwner().getUsername().equals(username);
    }
}