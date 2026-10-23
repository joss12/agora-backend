package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.ProductReview;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductReviewRepository
        extends JpaRepository<ProductReview, Long> {

    Page<ProductReview> findByProductId(
            Long productId,
            Pageable pageable
    );

    Optional<ProductReview> findByUserIdAndProductId(
            Long userId,
            Long productId
    );

    boolean existsByUserIdAndProductId(
            Long userId,
            Long productId
    );

    long countByProductId(Long productId);

    @Query("""
            SELECT AVG(r.rating)
            FROM ProductReview r
            WHERE r.product.id = :productId
            """)
    Double findAverageRatingByProductId(
            @Param("productId") Long productId
    );
}