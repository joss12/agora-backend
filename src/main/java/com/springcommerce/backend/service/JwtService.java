package com.springcommerce.backend.service;

import com.springcommerce.backend.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.expiration = expiration;
    }

    public String generateToken(User user) {

        Date now = new Date();

        Date expirationDate =
                new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())

                // Used to invalidate existing sessions
                .claim(
                        "tokenVersion",
                        user.getTokenVersion()
                )

                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {

        Claims claims = extractClaims(token);

        return claims.get(
                "userId",
                Long.class
        );
    }

    public Long extractTokenVersion(String token) {

        Claims claims = extractClaims(token);

        Object version = claims.get("tokenVersion");

        if (version instanceof Number number) {
            return number.longValue();
        }

        return null;
    }


    public boolean isTokenValid(String token) {

        try {

            Claims claims = extractClaims(token);

            Date expirationDate =
                    claims.getExpiration();

            return expirationDate != null
                    && expirationDate.after(new Date());

        } catch (Exception exception) {

            return false;
        }
    }

    public boolean isTokenVersionValid(
            String token,
            User user
    ) {

        try {

            Long jwtVersion =
                    extractTokenVersion(token);

            Long databaseVersion =
                    user.getTokenVersion();

            if (jwtVersion == null ||
                    databaseVersion == null) {

                return false;
            }

            return jwtVersion.equals(databaseVersion);

        } catch (Exception exception) {

            return false;
        }
    }

    private Claims extractClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expiration / 1000;
    }
}