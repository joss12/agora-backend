package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.WishlistItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository
        extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserIdOrderByCreatedAtDescIdDesc(
            Long userId
    );

    Optional<WishlistItem> findByUserIdAndProductId(
            Long userId,
            Long productId
    );

    boolean existsByUserIdAndProductId(
            Long userId,
            Long productId
    );

    long countByUserId(Long userId);
}