package com.springcommerce.backend.dto;

import java.time.LocalDateTime;

public record InventoryMovementResponse(
        Long id,
        Long productId,
        String type,
        Integer quantity,
        Integer stockBefore,
        Integer stockAfter,
        LocalDateTime created
) {
}
