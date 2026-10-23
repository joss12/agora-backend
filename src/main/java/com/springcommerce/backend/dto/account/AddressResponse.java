package com.springcommerce.backend.dto.account;

import java.time.LocalDateTime;

public record AddressResponse(
        Long id,
        String label,
        String recipientName,
        String phoneNumber,
        String postalCode,
        String address,
        String addressDetail,
        boolean defaultAddress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
