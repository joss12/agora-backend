package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.OrderItem;
import com.springcommerce.backend.entity.OrderStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository
        extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(
            Long orderId
    );

    @Query("""
            SELECT CASE WHEN COUNT(oi) > 0
                THEN true
                ELSE false
            END
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.user.id = :userId
              AND oi.product.id = :productId
              AND o.status = :status
            """)
    boolean existsPurchasedProductWithOrderStatus(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("status") OrderStatus status
    );
}