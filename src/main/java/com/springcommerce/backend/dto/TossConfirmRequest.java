package com.springcommerce.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TossConfirmRequest(

        @NotBlank
        @Size(max = 200)
        String paymentKey,

        @NotBlank
        @Size(min = 6, max = 64)
        String orderId,

        @NotNull
        @Positive
        Long amount

) {
}