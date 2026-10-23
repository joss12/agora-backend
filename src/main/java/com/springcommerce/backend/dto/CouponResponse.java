package com.springcommerce.backend.dto;

import com.springcommerce.backend.entity.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponResponse(

        Long id,

        String code,

        DiscountType discountType,

        BigDecimal discountValue,

        BigDecimal minimumOrderAmount,

        BigDecimal maximumDiscountAmount,

        Integer usageLimit,

        Integer usageLimitPerUser,

        Integer usageCount,

        LocalDateTime startsAt,

        LocalDateTime expiresAt,

        boolean active,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}
