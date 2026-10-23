package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository
        extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByCreatedAtAsc(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(
            Long cartId,
            Long productId
    );

    Optional<CartItem> findByIdAndCartId(
            Long id,
            Long cartId
    );

    void deleteByCartId(Long cartId);
}