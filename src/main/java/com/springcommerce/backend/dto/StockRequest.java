package com.springcommerce.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockRequest(
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater the zero")
        Integer quantity
) {
}
