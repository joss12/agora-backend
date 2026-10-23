package com.springcommerce.backend.dto;

public record TossPaymentStatusResponse(
        Long agoraOrderId,
        String tossOrderId,
        String tossStatus,
        boolean recoveryAllowed,
        String message
) {
}