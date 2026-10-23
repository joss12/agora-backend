package com.springcommerce.backend.dto.admin;

import com.springcommerce.backend.dto.AdminOrderResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminCustomerDetailResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long orderCount,
        long paidOrderCount,
        BigDecimal totalSpent,
        LocalDateTime lastOrderAt,
        List<AdminOrderResponse> orders
) {
}