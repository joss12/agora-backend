package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.PaymentAttemptResponse;
import com.springcommerce.backend.dto.PaymentResponse;

import com.springcommerce.backend.entity.Payment;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentNotFoundException;

import com.springcommerce.backend.repository.OrderRepository;
import com.springcommerce.backend.repository.PaymentAttemptRepository;
import com.springcommerce.backend.repository.PaymentRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;

    public PaymentService(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(
            String email,
            Long orderId
    ) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(
                        () -> new UsernameNotFoundException(
                                "User not found"
                        )
                );

        orderRepository
                .findByIdAndUserId(orderId, user.getId())
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        return mapToResponse(payment);
    }

    private PaymentResponse mapToResponse(
            Payment payment
    ) {
        List<PaymentAttemptResponse> attempts =
                attemptRepository
                        .findByPaymentIdOrderByIdAsc(
                                payment.getId()
                        )
                        .stream()
                        .map(attempt ->
                                new PaymentAttemptResponse(
                                        attempt.getId(),
                                        attempt.getOutcome().name(),
                                        attempt.getTransactionReference(),
                                        attempt.getCreatedAt()
                                )
                        )
                        .toList();

        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getTransactionReference(),
                payment.getTossOrderId(),
                attempts,
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}