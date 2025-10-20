package com.example.healthcheckapi.repository;

import com.example.healthcheckapi.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    List<Image> findByProduct_Id(Long productId);
    Optional<Image> findByImageIdAndProduct_Id(Long imageId, Long productId);
}