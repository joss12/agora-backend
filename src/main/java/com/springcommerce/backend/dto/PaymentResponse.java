package com.springcommerce.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        String status,
        String transactionReference,
        String tossOrderId,
        List<PaymentAttemptResponse> attempts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
