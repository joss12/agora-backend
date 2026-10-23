package com.springcommerce.backend.service;

import com.springcommerce.backend.entity.Order;
import com.springcommerce.backend.entity.Payment;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.exception.TossPaymentApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class OrderPaymentCancellationService {

    private final TossPaymentClient tossClient;

    public OrderPaymentCancellationService(
            TossPaymentClient tossClient
    ) {
        this.tossClient = tossClient;
    }

    /**
     * Called while OrderService holds the order's database lock.
     *
     * Returns the payment state that must be persisted when
     * the order cancellation is finalized.
     *
     * This method never marks an uncertain Toss payment as
     * successfully cancelled.
     */
    public PaymentStatus resolveCancellation(
            Order order,
            Payment payment
    ) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This payment has already been refunded."
            );
        }

        String tossOrderId = payment.getTossOrderId();

        // No Toss checkout was ever prepared.
        if (tossOrderId == null || tossOrderId.isBlank()) {

            // A locally simulated payment can be reversed
            // without calling Toss.
            if (payment.getStatus() == PaymentStatus.PAID) {
                return PaymentStatus.REFUNDED;
            }

            return payment.getStatus();
        }

        TossPaymentClient.TossPaymentResult result =
                lookupByOrderId(tossOrderId);

        verifyPayment(
                result,
                order,
                payment,
                tossOrderId
        );

        String status = result.status();

        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss returned an empty payment status."
            );
        }

        return switch (status) {

            // Confirmed payments require a real provider refund.
            case "DONE" -> refundCompletedPayment(
                    order,
                    payment,
                    result
            );

            // A full refund has already occurred at Toss.
            // Reconcile it locally.
            case "CANCELED" -> {
                if (result.balanceAmount() == null
                        || result.balanceAmount() != 0L) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Toss cancellation is not verified as a full refund."
                    );
                }

                yield PaymentStatus.REFUNDED;
            }

            // These attempts cannot be confirmed anymore.
            case "EXPIRED", "ABORTED" -> {
                if (payment.getStatus() == PaymentStatus.PAID) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Local payment is marked paid, but Toss "
                                    + "reports an unsuccessful attempt. "
                                    + "Manual reconciliation is required."
                    );
                }

                yield PaymentStatus.PENDING;
            }

            // Do not release stock while confirmation may
            // still be possible.
            case "READY", "IN_PROGRESS" ->
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Toss payment is still " + status
                                    + ". Please wait until the payment "
                                    + "expires or is resolved before cancelling."
                    );

            case "WAITING_FOR_DEPOSIT" ->
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Virtual account payment is awaiting deposit. "
                                    + "A dedicated virtual-account "
                                    + "cancellation flow is required."
                    );

            case "PARTIAL_CANCELED" ->
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This payment was partially refunded. "
                                    + "Manual reconciliation is required."
                    );

            default ->
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Unsupported Toss payment status: " + status
                    );
        };
    }

    private PaymentStatus refundCompletedPayment(
            Order order,
            Payment payment,
            TossPaymentClient.TossPaymentResult current
    ) {
        String paymentKey = current.paymentKey();

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss did not return a payment key."
            );
        }

        // One stable key per payment's full-refund operation.
        String idempotencyKey = UUID.nameUUIDFromBytes(
                ("AGORA-FULL-REFUND-" + payment.getId())
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();

        try {
            TossPaymentClient.TossPaymentResult refund =
                    tossClient.cancelPayment(
                            paymentKey,
                            "Customer cancelled before shipment",
                            idempotencyKey
                    );

            verifyPayment(
                    refund,
                    order,
                    payment,
                    payment.getTossOrderId()
            );

            if (!"CANCELED".equals(refund.status())
                    || refund.balanceAmount() == null
                    || refund.balanceAmount() != 0L) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Toss did not confirm a full refund."
                );
            }

            return PaymentStatus.REFUNDED;

        } catch (TossPaymentApiException exception) {
            // Toss might have completed a refund before
            // the API response was lost or rejected.
            return reconcileRefund(paymentKey, order, payment);

        } catch (RestClientException exception) {
            return reconcileRefund(paymentKey, order, payment);
        }
    }

    private PaymentStatus reconcileRefund(
            String paymentKey,
            Order order,
            Payment payment
    ) {
        try {
            TossPaymentClient.TossPaymentResult result =
                    tossClient.getPayment(paymentKey);

            verifyPayment(
                    result,
                    order,
                    payment,
                    payment.getTossOrderId()
            );

            if ("CANCELED".equals(result.status())
                    && result.balanceAmount() != null
                    && result.balanceAmount() == 0L) {
                return PaymentStatus.REFUNDED;
            }

        } catch (RestClientException exception) {
            // Keep the order unchanged if verification fails.
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Refund status is uncertain. "
                        + "The order has not been cancelled locally. "
                        + "Please retry after payment reconciliation."
        );
    }

    private TossPaymentClient.TossPaymentResult lookupByOrderId(
            String tossOrderId
    ) {
        try {
            TossPaymentClient.TossPaymentResult result =
                    tossClient.getPaymentByOrderId(tossOrderId);

            if (result == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Toss returned an empty payment response."
                );
            }

            return result;

        } catch (TossPaymentApiException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to verify payment with Toss.",
                    exception
            );

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss payment verification is unavailable.",
                    exception
            );
        }
    }

    private void verifyPayment(
            TossPaymentClient.TossPaymentResult result,
            Order order,
            Payment payment,
            String expectedTossOrderId
    ) {
        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss returned an empty payment response."
            );
        }

        if (!expectedTossOrderId.equals(result.orderId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss order ID mismatch."
            );
        }

        if (payment.getAmount() == null
                || order.getTotalAmount() == null
                || payment.getAmount().compareTo(
                order.getTotalAmount()
        ) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order and payment amounts do not match."
            );
        }

        long expectedAmount;

        try {
            expectedAmount =
                    payment.getAmount().longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid KRW payment amount."
            );
        }

        if (result.totalAmount() == null
                || result.totalAmount() != expectedAmount) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Toss payment amount mismatch."
            );
        }
    }
}