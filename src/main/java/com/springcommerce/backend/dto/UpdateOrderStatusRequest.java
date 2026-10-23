
package com.springcommerce.backend.dto;

import com.springcommerce.backend.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Order status is required")
        OrderStatus status
) {
}
