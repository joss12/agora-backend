package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM PasswordResetToken token
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
            UPDATE PasswordResetToken token
            SET token.usedAt = :usedAt
            WHERE token.user.id = :userId
              AND token.usedAt IS NULL
            """)
    int invalidateUnusedTokens(
            @Param("userId") Long userId,
            @Param("usedAt") LocalDateTime usedAt
    );
}