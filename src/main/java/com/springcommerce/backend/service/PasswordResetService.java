package com.springcommerce.backend.service;

import com.springcommerce.backend.entity.PasswordResetToken;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.repository.PasswordResetTokenRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import java.time.LocalDateTime;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private static final int EXPIRATION_MINUTES = 15;

    private final UserRepository userRepository;

    private final PasswordResetTokenRepository tokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final JavaMailSender mailSender;

    private final SecureRandom secureRandom =
            new SecureRandom();

    private final String frontendUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${app.frontend-url}") String frontendUrl
    ) {

        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void requestPasswordReset(String email) {

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElse(null);

        // Never reveal whether an email is registered.
        if (user == null || !user.isEnabled()) {
            return;
        }

        String rawToken = generateSecureToken();

        String tokenHash = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now();

        // Invalidate previously generated reset tokens.
        tokenRepository.invalidateUnusedTokens(
                user.getId(),
                now
        );

        PasswordResetToken resetToken =
                new PasswordResetToken();

        resetToken.setUser(user);
        resetToken.setTokenHash(tokenHash);

        resetToken.setExpiresAt(
                now.plusMinutes(EXPIRATION_MINUTES)
        );

        tokenRepository.save(resetToken);

        String resetLink =
                frontendUrl
                        + "/reset-password?token="
                        + rawToken;

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom("no-reply@agora.local");

        message.setTo(user.getEmail());

        message.setSubject(
                "Agora - Reset your password"
        );

        message.setText(
                """
                Hello %s,

                We received a request to reset your Agora password.

                Click the link below to create a new password:

                %s

                This link expires in 15 minutes.

                If you did not request a password reset,
                you can safely ignore this email.

                Agora Security Team
                """.formatted(
                        user.getFirstName(),
                        resetLink
                )
        );

        mailSender.send(message);
    }

    @Transactional
    public void resetPassword(
            String rawToken,
            String newPassword
    ) {

        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }

        if (newPassword == null ||
                newPassword.length() < 8 ||
                newPassword.length() > 128) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain 8 to 128 characters"
            );
        }

        String tokenHash =
                hashToken(rawToken);

        // Lock the token row during redemption.
        PasswordResetToken resetToken =
                tokenRepository
                        .findByTokenHashForUpdate(tokenHash)
                        .orElseThrow(this::invalidToken);

        if (resetToken.isUsed() ||
                resetToken.isExpired()) {

            throw invalidToken();
        }

        User user = resetToken.getUser();

        if (!user.isEnabled()) {
            throw invalidToken();
        }

        // 1. Update the user's password.
        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        // 2. Invalidate all previously issued JWTs.
        user.incrementTokenVersion();

        userRepository.save(user);

        LocalDateTime now = LocalDateTime.now();

        // 3. Invalidate all unused password-reset links.
        tokenRepository.invalidateUnusedTokens(
                user.getId(),
                now
        );
    }
    private String generateSecureToken() {

        byte[] bytes = new byte[TOKEN_BYTES];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private ResponseStatusException invalidToken() {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid or expired password reset token"
        );
    }
}