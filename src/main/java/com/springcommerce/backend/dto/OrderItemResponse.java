package com.springcommerce.backend.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        String sku,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {
}
