package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.UserAddress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository
        extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findAllByUserIdOrderByDefaultAddressDescCreatedAtDesc(
            Long userId
    );

    Optional<UserAddress> findByIdAndUserId(
            Long id,
            Long userId
    );

    boolean existsByUserId(
            Long userId
    );

    @Modifying
    @Query("""
            UPDATE UserAddress a
            SET a.defaultAddress = false
            WHERE a.user.id = :userId
              AND a.defaultAddress = true
            """)
    int clearDefaultAddress(
            @Param("userId") Long userId
    );
}