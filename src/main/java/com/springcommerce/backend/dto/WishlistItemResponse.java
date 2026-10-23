package com.springcommerce.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WishlistItemResponse(
        Long id,
        Long productId,
        String name,
        String sku,
        BigDecimal price,
        Integer stock,
        boolean active,
        String primaryImageUrl,
        LocalDateTime addedAt
) {
}
