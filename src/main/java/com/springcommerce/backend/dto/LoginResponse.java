package com.springcommerce.backend.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String email,
        String firstName,
        String role
) {
}
