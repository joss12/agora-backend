package com.springcommerce.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull(message = "Product is required")
        Long productId,

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        Integer quantity
) {
}
