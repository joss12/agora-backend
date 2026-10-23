package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.PaymentAttemptResponse;
import com.springcommerce.backend.dto.PaymentResponse;
import com.springcommerce.backend.dto.TossConfirmRequest;
import com.springcommerce.backend.dto.TossPrepareResponse;
import com.springcommerce.backend.dto.TossPaymentStatusResponse;

import com.springcommerce.backend.entity.*;

import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentConflictException;
import com.springcommerce.backend.exception.PaymentNotFoundException;
import com.springcommerce.backend.exception.TossPaymentApiException;

import com.springcommerce.backend.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class TossPaymentService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final TossPaymentClient tossClient;

    public TossPaymentService(
            UserRepository userRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            TossPaymentClient tossClient
    ) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.tossClient = tossClient;
    }

    // ============================================================
    // PREPARE PAYMENT
    // ============================================================

    @Transactional
    public TossPrepareResponse prepare(
            String email,
            Long orderId
    ) {
        User user = findUser(email);

        Order order = orderRepository
                .findByIdAndUserIdForUpdate(orderId, user.getId())
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new PaymentConflictException(
                    "Only placed orders can be paid"
            );
        }

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        if (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentConflictException(
                    "This payment cannot be started"
            );
        }

        long amount = validatedAmount(payment, order);

        if (payment.getTossOrderId() == null) {
            String tossOrderId = newTossOrderId(orderId);

            payment.setTossOrderId(tossOrderId);
            paymentRepository.save(payment);
        }

        return new TossPrepareResponse(
                orderId,
                payment.getTossOrderId(),
                amount,
                "KRW"
        );
    }

    // ============================================================
    // CONFIRM PAYMENT
    // ============================================================

    @Transactional
    public PaymentResponse confirm(
            String email,
            Long orderId,
            TossConfirmRequest request
    ) {
        User user = findUser(email);

        Order order = orderRepository
                .findByIdAndUserIdForUpdate(orderId, user.getId())
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        if (payment.getTossOrderId() == null) {
            throw new PaymentConflictException(
                    "Toss payment was not prepared"
            );
        }

        if (!payment.getTossOrderId().equals(request.orderId())) {
            throw new PaymentConflictException(
                    "Toss order ID does not match"
            );
        }

        long expectedAmount = validatedAmount(payment, order);

        if (request.amount() != expectedAmount) {
            throw new PaymentConflictException(
                    "Payment amount does not match the order"
            );
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            if (request.paymentKey().equals(
                    payment.getTransactionReference()
            )) {
                return mapToResponse(payment);
            }

            throw new PaymentConflictException(
                    "Order was paid using another payment"
            );
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentConflictException(
                    "Refunded payments cannot be confirmed"
            );
        }

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new PaymentConflictException(
                    "Order is not available for payment"
            );
        }

        String idempotencyKey = UUID.nameUUIDFromBytes(
                (
                        payment.getTossOrderId()
                                + ":"
                                + request.paymentKey()
                ).getBytes(StandardCharsets.UTF_8)
        ).toString();

        TossPaymentClient.TossPaymentResult result;

        try {
            result = tossClient.confirm(
                    request.paymentKey(),
                    request.orderId(),
                    expectedAmount,
                    idempotencyKey
            );

        } catch (TossPaymentApiException apiError) {
            if (apiError.isClientError()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Toss rejected the payment confirmation.",
                        apiError
                );
            }

            try {
                result = tossClient.getPayment(
                        request.paymentKey()
                );

            } catch (RestClientException lookupError) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Payment status is uncertain. "
                                + "Please retry with the same payment key.",
                        apiError
                );
            }

        } catch (RestClientException confirmationError) {
            try {
                result = tossClient.getPayment(
                        request.paymentKey()
                );

            } catch (RestClientException lookupError) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Payment status is uncertain. "
                                + "Please retry with the same payment key.",
                        confirmationError
                );
            }
        }

        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss returned an empty payment response"
            );
        }

        if (!request.paymentKey().equals(result.paymentKey())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss payment key mismatch"
            );
        }

        if (!request.orderId().equals(result.orderId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss order ID mismatch"
            );
        }

        if (result.totalAmount() == null
                || result.totalAmount() != expectedAmount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss confirmed an unexpected amount"
            );
        }

        if (!"DONE".equals(result.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment is not completed. Toss status: "
                            + result.status()
            );
        }

        payment.setStatus(PaymentStatus.PAID);
        payment.setTransactionReference(result.paymentKey());

        paymentRepository.save(payment);

        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setPayment(payment);
        attempt.setOutcome(PaymentOutcome.SUCCESS);
        attempt.setTransactionReference(
                "TOSS-" + UUID.randomUUID()
        );
        attempt.setProvider("TOSS");
        attempt.setProviderStatus("DONE");

        attemptRepository.save(attempt);

        return mapToResponse(payment);
    }

    // ============================================================
    // CHECK TOSS PAYMENT STATUS — READ ONLY
    // ============================================================

    @Transactional(readOnly = true)
    public TossPaymentStatusResponse checkPaymentStatus(
            String email,
            Long orderId
    ) {
        User user = findUser(email);

        Order order = orderRepository
                .findByIdAndUserId(orderId, user.getId())
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        String tossOrderId = payment.getTossOrderId();

        if (tossOrderId == null) {
            return new TossPaymentStatusResponse(
                    orderId,
                    null,
                    "NOT_PREPARED",
                    false,
                    "No Toss payment was prepared for this order."
            );
        }

        try {
            TossPaymentClient.TossPaymentResult result =
                    tossClient.getPaymentByOrderId(tossOrderId);

            verifyTossResult(
                    result,
                    tossOrderId,
                    validatedAmount(payment, order)
            );

            String tossStatus = result.status();

            if (tossStatus == null || tossStatus.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Toss returned an empty payment status."
                );
            }

            boolean recoveryAllowed =
                    "EXPIRED".equals(tossStatus)
                            && order.getStatus() == OrderStatus.PLACED
                            && payment.getStatus() == PaymentStatus.PENDING;

            String message = switch (tossStatus) {
                case "DONE" ->
                        "Toss reports a completed payment. "
                                + "Do not attempt another payment.";

                case "EXPIRED" ->
                        recoveryAllowed
                                ? "This Toss payment has expired. "
                                + "You can request a new payment attempt."
                                : "This payment has expired, "
                                + "but the order is not eligible for recovery.";

                case "CANCELED", "ABORTED" ->
                        "Toss reports an unsuccessful payment. "
                                + "Recovery is not available for this status.";

                default ->
                        "Toss payment status: "
                                + tossStatus
                                + ". Recovery is not available.";
            };

            return new TossPaymentStatusResponse(
                    orderId,
                    tossOrderId,
                    tossStatus,
                    recoveryAllowed,
                    message
            );

        } catch (TossPaymentApiException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to verify payment status with Toss.",
                    exception
            );

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss payment status is temporarily unavailable.",
                    exception
            );
        }
    }

    // ============================================================
    // RECOVER EXPIRED TOSS PAYMENT
    // ============================================================

    @Transactional
    public TossPrepareResponse recover(
            String email,
            Long orderId
    ) {
        User user = findUser(email);

        Order order = orderRepository
                .findByIdAndUserIdForUpdate(orderId, user.getId())
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new PaymentConflictException(
                    "Only placed orders can be recovered"
            );
        }

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentConflictException(
                    "Only pending payments can be recovered"
            );
        }

        String oldTossOrderId = payment.getTossOrderId();

        if (oldTossOrderId == null) {
            throw new PaymentConflictException(
                    "No Toss payment exists to recover"
            );
        }

        long amount = validatedAmount(payment, order);

        TossPaymentClient.TossPaymentResult result;

        try {
            result = tossClient.getPaymentByOrderId(
                    oldTossOrderId
            );

        } catch (TossPaymentApiException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to verify the expired payment with Toss.",
                    exception
            );

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss payment verification is temporarily unavailable.",
                    exception
            );
        }

        verifyTossResult(
                result,
                oldTossOrderId,
                amount
        );

        if (!"EXPIRED".equals(result.status())) {
            throw new PaymentConflictException(
                    "Recovery is only allowed for expired Toss payments"
            );
        }

        // Preserve the old Toss order ID in payment history.
        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setPayment(payment);
        attempt.setOutcome(PaymentOutcome.FAILURE);
        attempt.setTransactionReference(oldTossOrderId);
        attempt.setProvider("TOSS");
        attempt.setProviderStatus("EXPIRED");

        attemptRepository.save(attempt);

        // Rotate only after Toss has verified EXPIRED.
        String newTossOrderId = newTossOrderId(orderId);

        payment.setTossOrderId(newTossOrderId);
        paymentRepository.save(payment);

        return new TossPrepareResponse(
                orderId,
                newTossOrderId,
                amount,
                "KRW"
        );
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private String newTossOrderId(Long orderId) {
        return "AGORA-"
                + orderId
                + "-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "");
    }

    private void verifyTossResult(
            TossPaymentClient.TossPaymentResult result,
            String expectedOrderId,
            long expectedAmount
    ) {
        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss returned an empty payment response."
            );
        }

        if (!expectedOrderId.equals(result.orderId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss returned an unexpected order ID."
            );
        }

        if (result.totalAmount() == null
                || result.totalAmount() != expectedAmount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss returned an unexpected payment amount."
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

    private long validatedAmount(
            Payment payment,
            Order order
    ) {
        BigDecimal paymentAmount = payment.getAmount();
        BigDecimal orderAmount = order.getTotalAmount();

        if (paymentAmount == null || orderAmount == null) {
            throw new PaymentConflictException(
                    "Order payment amount is missing"
            );
        }

        if (paymentAmount.compareTo(orderAmount) != 0) {
            throw new PaymentConflictException(
                    "Payment amount differs from order total"
            );
        }

        long amount;

        try {
            amount = paymentAmount.longValueExact();

        } catch (ArithmeticException exception) {
            throw new PaymentConflictException(
                    "KRW payment amount must be a whole number"
            );
        }

        if (amount <= 0) {
            throw new PaymentConflictException(
                    "Payment amount must be positive"
            );
        }

        return amount;
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