package com.springcommerce.backend.service;

import com.springcommerce.backend.controller.DemoPaymentController;
import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentSimulationSecurityTest {

    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private PaymentAttemptRepository paymentAttemptRepository;

    private DemoPaymentController controller;

    private User customer;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {

        userRepository = mock(UserRepository.class);
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        paymentAttemptRepository = mock(PaymentAttemptRepository.class);

        // Enable the existing simulator only inside this unit test.
        controller = new DemoPaymentController(
                userRepository,
                orderRepository,
                paymentRepository,
                paymentAttemptRepository,
                true
        );

        // =====================================================
        // CUSTOMER
        // =====================================================

        customer = mock(User.class);

        when(customer.getId()).thenReturn(1L);
        when(customer.getEmail()).thenReturn("test@example.com");

        // =====================================================
        // ORDER
        // =====================================================

        order = mock(Order.class);

        when(order.getId()).thenReturn(10L);
        when(order.getStatus()).thenReturn(OrderStatus.PLACED);

        // =====================================================
        // PAYMENT
        // =====================================================

        payment = new Payment();

        payment.setOrder(order);
        payment.setAmount(new BigDecimal("20000.00"));
        payment.setStatus(PaymentStatus.PENDING);

        // =====================================================
        // REPOSITORY MOCKS
        // =====================================================

        when(userRepository.findByEmail(customer.getEmail()))
                .thenReturn(Optional.of(customer));

        when(orderRepository.findByIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(payment));
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private TestingAuthenticationToken authentication() {

        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        customer.getEmail(),
                        null,
                        "ROLE_CUSTOMER"
                );

        authentication.setAuthenticated(true);

        return authentication;
    }

    private DemoPaymentController.DemoPaymentRequest request(
            DemoPaymentController.DemoOutcome outcome
    ) {
        return new DemoPaymentController.DemoPaymentRequest(outcome);
    }

    // =========================================================
    // TEST 1: UNAUTHENTICATED REQUEST
    // =========================================================

    @Test
    void unauthenticatedRequestIsRejected() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        null,
                        10L,
                        request(DemoPaymentController.DemoOutcome.SUCCESS)
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                orderRepository,
                paymentRepository,
                paymentAttemptRepository
        );
    }

    // =========================================================
    // TEST 2: ORDER OWNERSHIP
    // =========================================================

    @Test
    void anotherCustomerCannotSimulatePayment() {

        when(orderRepository.findByIdAndUserIdForUpdate(10L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(DemoPaymentController.DemoOutcome.SUCCESS)
                )
        );

        verify(paymentRepository, never())
                .findByOrderId(anyLong());

        verifyNoInteractions(paymentAttemptRepository);
    }

    // =========================================================
    // TEST 3: SUCCESSFUL DEMO PAYMENT
    // =========================================================

    @Test
    void successfulDemoMarksPaymentAsPaid() {

        var response = controller.simulate(
                authentication(),
                10L,
                request(DemoPaymentController.DemoOutcome.SUCCESS)
        );

        assertEquals("PAID", response.status());
        assertEquals("SUCCESS", response.outcome());
        assertEquals("DEMO", response.provider());

        assertTrue(response.demo());
        assertTrue(response.persisted());

        assertNotNull(response.transactionReference());

        assertTrue(
                response.transactionReference().startsWith("DEMO-")
        );

        assertEquals(
                PaymentStatus.PAID,
                payment.getStatus()
        );

        assertEquals(
                response.transactionReference(),
                payment.getTransactionReference()
        );

        verify(paymentRepository, times(1))
                .saveAndFlush(same(payment));

        verify(paymentAttemptRepository, times(1))
                .save(argThat(attempt ->
                        attempt.getPayment() == payment
                                && attempt.getOutcome() == PaymentOutcome.SUCCESS
                                && "DEMO".equals(attempt.getProvider())
                                && "SIMULATED_SUCCESS".equals(
                                attempt.getProviderStatus()
                        )
                                && response.transactionReference().equals(
                                attempt.getTransactionReference()
                        )
                ));
    }

    // =========================================================
    // TEST 4: FAILED DEMO PAYMENT
    // =========================================================

    @Test
    void failedDemoMarksPaymentAsFailed() {

        var response = controller.simulate(
                authentication(),
                10L,
                request(DemoPaymentController.DemoOutcome.FAILURE)
        );

        assertEquals("FAILED", response.status());
        assertEquals("FAILURE", response.outcome());
        assertEquals("DEMO", response.provider());

        assertTrue(response.demo());
        assertTrue(response.persisted());

        assertNotNull(response.transactionReference());

        assertTrue(
                response.transactionReference().startsWith("DEMO-")
        );

        assertEquals(
                PaymentStatus.FAILED,
                payment.getStatus()
        );

        // A failed attempt does not become a successful
        // transaction reference on the payment.
        assertNull(payment.getTransactionReference());

        verify(paymentRepository, times(1))
                .saveAndFlush(same(payment));

        verify(paymentAttemptRepository, times(1))
                .save(argThat(attempt ->
                        attempt.getPayment() == payment
                                && attempt.getOutcome() == PaymentOutcome.FAILURE
                                && "DEMO".equals(attempt.getProvider())
                                && "SIMULATED_FAILURE".equals(
                                attempt.getProviderStatus()
                        )
                                && response.transactionReference().equals(
                                attempt.getTransactionReference()
                        )
                ));
    }

    // =========================================================
    // TEST 5: PAID ORDER
    // =========================================================

    @Test
    void paidOrderCannotUseDemoPayment() {

        payment.setStatus(PaymentStatus.PAID);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(DemoPaymentController.DemoOutcome.SUCCESS)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(paymentRepository, never())
                .saveAndFlush(any(Payment.class));

        verifyNoInteractions(paymentAttemptRepository);
    }

    // =========================================================
    // TEST 6: REFUNDED ORDER
    // =========================================================

    @Test
    void refundedOrderCannotUseDemoPayment() {

        payment.setStatus(PaymentStatus.REFUNDED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(DemoPaymentController.DemoOutcome.SUCCESS)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(paymentRepository, never())
                .saveAndFlush(any(Payment.class));

        verifyNoInteractions(paymentAttemptRepository);
    }

    // =========================================================
    // TEST 7: TOSS PREPARED ORDER
    // =========================================================

    @Test
    void tossPreparedOrderCannotUseDemoPayment() {

        payment.setTossOrderId("TOSS-TEST-123");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(DemoPaymentController.DemoOutcome.SUCCESS)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        verify(paymentRepository, never())
                .saveAndFlush(any(Payment.class));

        verifyNoInteractions(paymentAttemptRepository);
    }

    // =========================================================
    // TEST 8: FAILED PAYMENT CAN BE RETRIED
    // =========================================================

    @Test
    void failedDemoPaymentCanBeFollowedBySuccessfulDemo() {

        var failed = controller.simulate(
                authentication(),
                10L,
                request(DemoPaymentController.DemoOutcome.FAILURE)
        );

        assertEquals("FAILED", failed.status());

        assertEquals(
                PaymentStatus.FAILED,
                payment.getStatus()
        );

        var successful = controller.simulate(
                authentication(),
                10L,
                request(DemoPaymentController.DemoOutcome.SUCCESS)
        );

        assertEquals("PAID", successful.status());

        assertNotEquals(
                failed.transactionReference(),
                successful.transactionReference()
        );

        assertTrue(failed.persisted());
        assertTrue(successful.persisted());

        assertEquals(
                PaymentStatus.PAID,
                payment.getStatus()
        );

        assertEquals(
                successful.transactionReference(),
                payment.getTransactionReference()
        );

        verify(paymentRepository, times(2))
                .saveAndFlush(same(payment));

        verify(paymentAttemptRepository, times(2))
                .save(any(PaymentAttempt.class));
    }

    // =========================================================
    // TEST 9: SUCCESSFUL PAYMENT CANNOT BE REPEATED
    // =========================================================

    @Test
    void successfulDemoPaymentCannotBeRepeated() {

        controller.simulate(
                authentication(),
                10L,
                request(DemoPaymentController.DemoOutcome.SUCCESS)
        );

        assertEquals(
                PaymentStatus.PAID,
                payment.getStatus()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(DemoPaymentController.DemoOutcome.SUCCESS)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(paymentRepository, times(1))
                .saveAndFlush(same(payment));

        verify(paymentAttemptRepository, times(1))
                .save(any(PaymentAttempt.class));
    }

    // =========================================================
    // TEST 10: DEMO PAYMENTS DISABLED
    // =========================================================

    @Test
    void demoPaymentIsRejectedWhenDisabled() {

        DemoPaymentController disabledController =
                new DemoPaymentController(
                        userRepository,
                        orderRepository,
                        paymentRepository,
                        paymentAttemptRepository,
                        false
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> disabledController.simulate(
                        authentication(),
                        10L,
                        request(DemoPaymentController.DemoOutcome.SUCCESS)
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                orderRepository,
                paymentRepository,
                paymentAttemptRepository
        );
    }
}