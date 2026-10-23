package com.springcommerce.backend.dto;

import com.springcommerce.backend.entity.DiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String code,

        @NotNull
        DiscountType discountType,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal discountValue,

        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal minimumOrderAmount,

        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal maximumDiscountAmount,

        @Positive
        Integer usageLimit,

        @Positive
        Integer usageLimitPerUser,

        @NotNull
        LocalDateTime startsAt,

        @NotNull
        LocalDateTime expiresAt,

        @NotNull
        Boolean active
) {
}