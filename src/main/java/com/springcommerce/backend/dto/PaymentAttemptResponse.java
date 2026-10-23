
package com.springcommerce.backend.dto;

import java.time.LocalDateTime;

public record PaymentAttemptResponse(
        Long id,
        String outcome,
        String transactionReference,
        LocalDateTime createdAt
) {
}

