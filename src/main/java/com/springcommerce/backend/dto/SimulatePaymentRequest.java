
package com.springcommerce.backend.dto;

import com.springcommerce.backend.entity.PaymentOutcome;

import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(
        @NotNull
        PaymentOutcome outcome
) {
}
