package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.PaymentAttemptResponse;
import com.springcommerce.backend.dto.PaymentResponse;

import com.springcommerce.backend.entity.*;

import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentConflictException;
import com.springcommerce.backend.exception.PaymentNotFoundException;

import com.springcommerce.backend.repository.*;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final Environment environment;

    public PaymentService(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            Environment environment
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(
            String email,
            Long orderId
    ) {
        User user = findUser(email);

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

    @Transactional
    public PaymentResponse simulate(
            String email,
            Long orderId,
            PaymentOutcome outcome
    ) {
        // ========================================================
        // Security — simulated payments are development-only
        // ========================================================

        validateSimulationEnvironment();

        User user = findUser(email);

        // Payment, cancellation and admin fulfillment
        // all acquire this order lock first.
        Order order = orderRepository
                .findByIdAndUserIdForUpdate(
                        orderId,
                        user.getId()
                )
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new PaymentConflictException(
                    "Payment is only allowed for placed orders"
            );
        }

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        // Prevent simulated payments from bypassing Toss Payments.
        if (payment.getTossOrderId() != null) {
            throw new PaymentConflictException(
                    "This order uses Toss Payments. Complete payment through Toss."
            );
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new PaymentConflictException(
                    "This order is already paid"
            );
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentConflictException(
                    "A refunded payment cannot be retried"
            );
        }

        String reference =
                "SIM-" + UUID.randomUUID();

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(payment);
        attempt.setOutcome(outcome);
        attempt.setTransactionReference(reference);

        attemptRepository.save(attempt);

        if (outcome == PaymentOutcome.SUCCESS) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setTransactionReference(reference);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);

        return mapToResponse(payment);
    }

    private void validateSimulationEnvironment() {

        List<String> activeProfiles =
                Arrays.asList(environment.getActiveProfiles());

        boolean production =
                activeProfiles.contains("prod");

        boolean developmentOrTest =
                activeProfiles.contains("dev")
                        || activeProfiles.contains("test");

        if (production || !developmentOrTest) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Simulated payments are disabled in this environment."
            );
        }
    }

    private User findUser(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(
                        () -> new UsernameNotFoundException(
                                "User not found"
                        )
                );
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