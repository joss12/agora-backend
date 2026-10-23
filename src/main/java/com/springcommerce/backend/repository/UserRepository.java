package com.springcommerce.backend.repository;

import com.springcommerce.backend.dto.admin.AdminCustomerResponse;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.entity.UserRole;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndRole(
            Long id,
            UserRole role
    );

    @Query(
            value = """
                    SELECT new com.springcommerce.backend.dto.admin.AdminCustomerResponse(
                        u.id,
                        u.firstName,
                        u.lastName,
                        u.email,
                        u.enabled,
                        COUNT(DISTINCT o.id),
                        COUNT(DISTINCT CASE
                            WHEN p.status = :paidStatus
                            THEN o.id
                        END),
                        COALESCE(
                            SUM(
                                CASE
                                    WHEN p.status = :paidStatus
                                    THEN p.amount
                                    ELSE 0
                                END
                            ),
                            0
                        ),
                        MAX(o.createdAt),
                        u.createdAt
                    )
                    FROM User u
                    LEFT JOIN Order o
                        ON o.user = u
                    LEFT JOIN Payment p
                        ON p.order = o
                    WHERE u.role = :role
                    GROUP BY
                        u.id,
                        u.firstName,
                        u.lastName,
                        u.email,
                        u.enabled,
                        u.createdAt
                    ORDER BY u.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(u)
                    FROM User u
                    WHERE u.role = :role
                    """
    )
    Page<AdminCustomerResponse> findCustomerSummaries(
            @Param("role") UserRole role,
            @Param("paidStatus") PaymentStatus paidStatus,
            Pageable pageable
    );
}