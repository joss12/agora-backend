package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionRepository
        extends JpaRepository<CouponRedemption, Long> {

    long countByCouponIdAndUserId(Long couponId, Long userId);

    Optional<CouponRedemption> findByOrderId(Long orderId);
}
