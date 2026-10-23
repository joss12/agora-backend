package com.springcommerce.backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        long orderCount,
        long paidOrderCount,
        BigDecimal totalSpent,
        LocalDateTime lastOrderAt,
        LocalDateTime createdAt
) {
}
