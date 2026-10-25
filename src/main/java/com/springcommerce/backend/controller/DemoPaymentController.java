package com.springcommerce.backend.controller;

import com.springcommerce.backend.entity.Order;
import com.springcommerce.backend.entity.OrderStatus;
import com.springcommerce.backend.entity.Payment;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentNotFoundException;

import com.springcommerce.backend.repository.OrderRepository;
import com.springcommerce.backend.repository.PaymentRepository;
import com.springcommerce.backend.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders/{orderId}/payment/demo")
public class DemoPaymentController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public DemoPaymentController(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    public enum DemoOutcome {
        SUCCESS,
        FAILURE
    }

    public record DemoPaymentRequest(
            @NotNull DemoOutcome outcome
    ) {
    }

    public record DemoPaymentResponse(
            Long orderId,
            BigDecimal amount,
            String status,
            String outcome,
            String transactionReference,
            String provider,
            boolean demo,
            boolean persisted,
            LocalDateTime createdAt,
            String message
    ) {
    }

    @PostMapping
    @Transactional(readOnly = true)
    public DemoPaymentResponse simulate(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody DemoPaymentRequest request
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        if (request == null || request.outcome() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment outcome is required."
            );
        }

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(
                        () -> new UsernameNotFoundException(
                                "User not found"
                        )
                );

        Order order = orderRepository
                .findByIdAndUserId(
                        orderId,
                        user.getId()
                )
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order is no longer awaiting payment."
            );
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order has already been paid."
            );
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Refunded payments cannot be retried."
            );
        }

        if (payment.getTossOrderId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order uses Toss Payments."
            );
        }

        boolean success =
                request.outcome() == DemoOutcome.SUCCESS;

        String reference =
                "DEMO-" + UUID.randomUUID();

        return new DemoPaymentResponse(
                order.getId(),
                payment.getAmount(),
                success
                        ? "SIMULATED_SUCCESS"
                        : "SIMULATED_FAILURE",
                request.outcome().name(),
                reference,
                "DEMO",
                true,
                false,
                LocalDateTime.now(),
                success
                        ? "Simulation completed. No payment was collected."
                        : "Simulation failed. No payment was collected."
        );
    }
}