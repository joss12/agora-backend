package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository
        extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);



    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT c
    FROM Cart c
    WHERE c.user.id = :userId
    """)
    Optional<Cart> findByUserIdForUpdate(
            @Param("userId") Long userId
    );
}