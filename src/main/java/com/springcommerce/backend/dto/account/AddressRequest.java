package com.springcommerce.backend.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank
        @Size(max = 50)
        String label,

        @NotBlank
        @Size(max = 200)
        String recipientName,

        @NotBlank
        @Size(max = 30)
        String phoneNumber,

        @NotBlank
        @Size(max = 20)
        String postalCode,

        @NotBlank
        @Size(max = 500)
        String address,

        @Size(max = 500)
        String addressDetail,

        boolean defaultAddress
) {
}
