package com.example.healthcheckapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "images", indexes = {
        @Index(name = "idx_images_product", columnList = "product_id")
})
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    @JsonProperty("image_id")
    private Long imageId;

    @Column(name = "file_name", nullable = false)
    @JsonProperty("file_name")
    private String fileName;

    @Column(name = "date_created", nullable = false, updatable = false)
    @JsonProperty("date_created")
    private Instant dateCreated;

    @Column(name = "s3_bucket_path", nullable = false)
    @JsonProperty("s3_bucket_path")
    private String s3BucketPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @JsonProperty(value = "product_id", access = JsonProperty.Access.READ_ONLY)
    public Long getProductId() {
        return product != null ? product.getId() : null;
    }

    @PrePersist
    protected void onCreate() {
        dateCreated = Instant.now();
    }

    // Getters and Setters
    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Instant getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Instant dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getS3BucketPath() {
        return s3BucketPath;
    }

    public void setS3BucketPath(String s3BucketPath) {
        this.s3BucketPath = s3BucketPath;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}