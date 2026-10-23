package com.springcommerce.backend.dto;
import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        Integer stock,
        CategorySummary category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String primaryImageUrl,
        List<ProductImageResponse> images
) {
}