package com.springcommerce.backend.controller;

import com.springcommerce.backend.entity.Order;
import com.springcommerce.backend.entity.OrderStatus;
import com.springcommerce.backend.entity.Payment;
import com.springcommerce.backend.entity.PaymentAttempt;
import com.springcommerce.backend.entity.PaymentOutcome;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentNotFoundException;

import com.springcommerce.backend.repository.OrderRepository;
import com.springcommerce.backend.repository.PaymentAttemptRepository;
import com.springcommerce.backend.repository.PaymentRepository;
import com.springcommerce.backend.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
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
    private final PaymentAttemptRepository attemptRepository;
    private final boolean demoEnabled;

    public DemoPaymentController(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            @Value("${app.payment.demo-enabled:false}")
            boolean demoEnabled
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.demoEnabled = demoEnabled;
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
    @Transactional
    public DemoPaymentResponse simulate(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody DemoPaymentRequest request
    ) {
        if (!demoEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Demo payments are disabled on this server."
            );
        }

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

        // Lock the order so two simultaneous requests
        // cannot successfully pay the same order twice.
        Order order = orderRepository
                .findByIdAndUserIdForUpdate(
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
                    "Only placed orders can be paid."
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
                    "This order is associated with Toss Payments."
            );
        }

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment cannot be simulated in its current state."
            );
        }

        boolean success =
                request.outcome() == DemoOutcome.SUCCESS;

        String reference =
                "DEMO-" + UUID.randomUUID();

        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setPayment(payment);
        attempt.setOutcome(
                success
                        ? PaymentOutcome.SUCCESS
                        : PaymentOutcome.FAILURE
        );
        attempt.setProvider("DEMO");
        attempt.setProviderStatus(
                success ? "SIMULATED_SUCCESS" : "SIMULATED_FAILURE"
        );
        attempt.setTransactionReference(reference);

        attemptRepository.save(attempt);

        payment.setStatus(
                success
                        ? PaymentStatus.PAID
                        : PaymentStatus.FAILED
        );

        if (success) {
            payment.setTransactionReference(reference);
        }

        paymentRepository.saveAndFlush(payment);

        return new DemoPaymentResponse(
                order.getId(),
                payment.getAmount(),
                payment.getStatus().name(),
                request.outcome().name(),
                reference,
                "DEMO",
                true,
                true,
                LocalDateTime.now(),
                success
                        ? "Demo payment recorded successfully. No real money was charged."
                        : "Demo payment failed. You can retry."
        );
    }
}