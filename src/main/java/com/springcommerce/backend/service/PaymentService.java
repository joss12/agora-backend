package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.PaymentAttemptResponse;
import com.springcommerce.backend.dto.PaymentResponse;

import com.springcommerce.backend.entity.*;

import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentConflictException;
import com.springcommerce.backend.exception.PaymentNotFoundException;

import com.springcommerce.backend.repository.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log =
            LoggerFactory.getLogger(PaymentService.class);

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

    // ============================================================
    // GET PAYMENT
    // ============================================================

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(
            String email,
            Long orderId
    ) {

        User user = findUser(email);

        orderRepository
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

        return mapToResponse(payment);
    }

    // ============================================================
    // SIMULATE PAYMENT
    // ============================================================

    @Transactional
    public PaymentResponse simulate(
            String email,
            Long orderId,
            PaymentOutcome outcome
    ) {

        // Check whether simulated payments are allowed.
        validateSimulationEnvironment();

        if (outcome == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment outcome is required."
            );
        }

        User user = findUser(email);

        // Lock the order to prevent concurrent payment,
        // cancellation, or fulfillment changes.
        Order order = orderRepository
                .findByIdAndUserIdForUpdate(
                        orderId,
                        user.getId()
                )
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        // Only placed orders can be paid.
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

        // ========================================================
        // TOSS PAYMENT PROTECTION
        // ========================================================

        // Never allow a simulated payment to bypass Toss.
        if (payment.getTossOrderId() != null) {
            throw new PaymentConflictException(
                    "This order uses Toss Payments. "
                            + "Complete payment through Toss."
            );
        }

        // ========================================================
        // PAYMENT STATUS VALIDATION
        // ========================================================

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

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.FAILED) {

            throw new PaymentConflictException(
                    "Payment cannot be simulated in its current status"
            );
        }

        // ========================================================
        // CREATE SIMULATED PAYMENT ATTEMPT
        // ========================================================

        String reference =
                "SIM-" + UUID.randomUUID();

        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setPayment(payment);
        attempt.setOutcome(outcome);
        attempt.setTransactionReference(reference);

        attemptRepository.save(attempt);

        // ========================================================
        // UPDATE PAYMENT STATUS
        // ========================================================

        if (outcome == PaymentOutcome.SUCCESS) {

            payment.setStatus(PaymentStatus.PAID);
            payment.setTransactionReference(reference);

            log.info(
                    "Simulated payment succeeded for orderId={}",
                    orderId
            );

        } else {

            payment.setStatus(PaymentStatus.FAILED);

            log.info(
                    "Simulated payment failed for orderId={}",
                    orderId
            );
        }

        paymentRepository.save(payment);

        return mapToResponse(payment);
    }

    // ============================================================
    // SIMULATION ENVIRONMENT VALIDATION
    // ============================================================

    private void validateSimulationEnvironment() {

        List<String> activeProfiles =
                Arrays.asList(environment.getActiveProfiles());

        boolean production =
                activeProfiles.contains("prod")
                        || activeProfiles.contains("production");

        boolean developmentOrTest =
                activeProfiles.contains("dev")
                        || activeProfiles.contains("test");

        // Spring Boot maps:
        //
        // APP_PAYMENT_SIMULATION_ENABLED
        //
        // to:
        //
        // app.payment.simulation-enabled
        //
        // Read the canonical property first, then check the
        // explicit environment variable as a fallback.

        boolean simulationEnabled =
                environment.getProperty(
                        "app.payment.simulation-enabled",
                        Boolean.class,
                        false
                );

        String explicitEnvironmentValue =
                System.getenv(
                        "APP_PAYMENT_SIMULATION_ENABLED"
                );

        if (explicitEnvironmentValue != null) {

            simulationEnabled =
                    Boolean.parseBoolean(
                            explicitEnvironmentValue.trim()
                    );
        }

        log.info(
                "Payment simulation configuration: "
                        + "profiles={}, production={}, enabled={}",
                activeProfiles,
                production,
                simulationEnabled
        );

        // ========================================================
        // DEVELOPMENT / TEST
        // ========================================================

        // Simulations are automatically available in
        // development and test environments.

        if (!production && developmentOrTest) {
            return;
        }

        // ========================================================
        // PRODUCTION
        // ========================================================

        // Never enable simulated payments on the public
        // production storefront.
        //
        // A simulated SUCCESS marks an order as PAID
        // without a real payment transaction.
        //
        // Test production-like deployments in a separate,
        // access-controlled staging environment instead.

        if (production) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Simulated payments are disabled in production."
            );
        }

        // ========================================================
        // OTHER ENVIRONMENTS
        // ========================================================

        // Allow explicit opt-in for isolated staging environments.

        if (simulationEnabled) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Simulated payments are disabled in this environment."
        );
    }

    // ============================================================
    // FIND USER
    // ============================================================

    private User findUser(String email) {

        return userRepository
                .findByEmail(email)
                .orElseThrow(
                        () -> new UsernameNotFoundException(
                                "User not found"
                        )
                );
    }

    // ============================================================
    // MAP PAYMENT RESPONSE
    // ============================================================

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