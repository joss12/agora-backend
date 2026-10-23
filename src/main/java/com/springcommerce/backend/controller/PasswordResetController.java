package com.springcommerce.backend.controller;

import com.springcommerce.backend.security.PasswordResetRateLimiter;
import com.springcommerce.backend.service.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final PasswordResetRateLimiter rateLimiter;

    public PasswordResetController(
            PasswordResetService passwordResetService,
            PasswordResetRateLimiter rateLimiter
    ) {
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimiter.checkForgotPassword(httpRequest);

        passwordResetService.requestPasswordReset(
                request.email()
        );

        return ResponseEntity.ok(
                new MessageResponse(
                        "If an account exists for this email, a password-reset link will be sent."
                )
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimiter.checkResetPassword(httpRequest);

        passwordResetService.resetPassword(
                request.token(),
                request.newPassword()
        );

        return ResponseEntity.ok(
                new MessageResponse(
                        "Password reset successfully."
                )
        );
    }

    public record ForgotPasswordRequest(
            @NotBlank
            @Email
            String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank
            String token,

            @NotBlank
            @Size(min = 8, max = 128)
            String newPassword
    ) {}

    public record MessageResponse(
            String message
    ) {}
}