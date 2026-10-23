
package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.Order;
import com.springcommerce.backend.entity.OrderStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    Optional<Order> findByIdAndUserId(
            Long orderId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.id = :orderId
              AND o.user.id = :userId
            """)
    Optional<Order> findByIdAndUserIdForUpdate(
            @Param("orderId") Long orderId,
            @Param("userId") Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o
            FROM Order o
            WHERE o.id = :orderId
            """)
    Optional<Order> findByIdForUpdate(
            @Param("orderId") Long orderId
    );

    Page<Order> findAllByOrderByCreatedAtDesc(
            Pageable pageable
    );

    Page<Order> findByStatusOrderByCreatedAtDesc(
            OrderStatus status,
            Pageable pageable
    );
}
