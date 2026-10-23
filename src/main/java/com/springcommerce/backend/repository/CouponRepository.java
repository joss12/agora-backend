package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCaseAndDeletedFalse(
            String code
    );

    Optional<Coupon> findByIdAndDeletedFalse(Long id);

    List<Coupon> findByDeletedFalseOrderByCreatedAtDesc();

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM Coupon c
            WHERE UPPER(c.code) = :code
              AND c.deleted = false
            """)
    Optional<Coupon> findByCodeForUpdate(
            @Param("code") String code
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(
            @Param("id") Long id
    );
}