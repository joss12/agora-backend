package com.springcommerce.backend.dto;

import java.time.LocalDateTime;

public record ShipmentResponse(
        Long id,
        Long orderId,
        String carrier,
        String trackingNumber,
        String status,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}