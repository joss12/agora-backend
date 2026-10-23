package com.springcommerce.backend.dto;

public record ProductRatingSummaryResponse(
        Long productId,
        Double averageRating,
        long reviewCount
) {}