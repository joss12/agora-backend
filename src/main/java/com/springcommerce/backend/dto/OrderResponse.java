
package com.springcommerce.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        String recipientName,
        String phoneNumber,
        String postalCode,
        String address,
        String addressDetail,
        String deliveryInstructions,
        LocalDateTime createdAt
) {
}

