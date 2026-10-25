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
    private final boolean demoCheckoutEnabled;

    public DemoPaymentController(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            @Value("${app.payment.demo-checkout-enabled:false}")
            boolean demoCheckoutEnabled
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.demoCheckoutEnabled = demoCheckoutEnabled;
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
        if (!demoCheckoutEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Demo checkout is disabled."
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

        // Lock the order to serialize payment attempts
        // and prevent concurrent payment/cancellation changes.
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

        // Never simulate payment for an order
        // that has already entered fulfillment.
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only placed orders can be paid."
            );
        }

        // A completed payment cannot be paid again.
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

        // Do not interfere with Toss payment sessions.
        if (payment.getTossOrderId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This order uses Toss Payments."
            );
        }

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.FAILED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment cannot be simulated in its current status."
            );
        }

        boolean success =
                request.outcome() == DemoOutcome.SUCCESS;

        String reference =
                "DEMO-" + UUID.randomUUID();

        if (success) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setTransactionReference(reference);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);

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
                        ? "Demo payment completed. No money was charged."
                        : "Demo payment failed. You can retry."
        );
    }
}