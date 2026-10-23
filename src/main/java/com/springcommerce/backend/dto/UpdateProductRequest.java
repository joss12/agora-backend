package com.springcommerce.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateProductRequest(

        @NotBlank(message = "Product name is required")
        String name,

        @NotBlank(message = "SKU is required")
        String sku,

        String description,

        @NotNull(message = "Price is required")
        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal price,

        @NotNull(message = "Stock is required")
        @PositiveOrZero(message = "Stock cannot be negative")
        Integer stock,

        @NotNull(message = "Category is required")
        Long categoryId

) {
}