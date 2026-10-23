package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository
        extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAscIdAsc(
            Long productId
    );

    Optional<ProductImage> findByIdAndProductId(
            Long imageId,
            Long productId
    );

    Optional<ProductImage> findByProductIdAndPrimaryTrue(
            Long productId
    );

    @Query("""
            SELECT image
            FROM ProductImage image
            WHERE image.product.id IN :productIds
            ORDER BY image.product.id ASC,
                     image.primary DESC,
                     image.displayOrder ASC,
                     image.id ASC
            """)
    List<ProductImage> findWishlistImages(
            @Param("productIds") Collection<Long> productIds
    );
}