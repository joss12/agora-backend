package com.springcommerce.backend.dto;

import java.time.LocalDateTime;

public record ProductReviewResponse(
        Long id,
        Long productId,
        Long customerId,
        String customerName,
        Integer rating,
        String title,
        String content,
        boolean verifiedPurchase,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}