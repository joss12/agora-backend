package com.springcommerce.backend.dto;

public record TossPrepareResponse(
        Long orderId,
        String tossOrderId,
        long amount,
        String currency
) {
}