
package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.PaymentAttempt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAttemptRepository
        extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentIdOrderByIdAsc(
            Long paymentId
    );
}

