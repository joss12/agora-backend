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
import static org.mockito.Mockito.*;

class PaymentSimulationSecurityTest {

    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;

    private DemoPaymentController controller;

    private User customer;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {

        userRepository = mock(UserRepository.class);
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);

        controller = new DemoPaymentController(
                userRepository,
                orderRepository,
                paymentRepository
        );

        // =====================================================
        // CUSTOMER
        // =====================================================

        customer = mock(User.class);

        when(customer.getId())
                .thenReturn(1L);

        when(customer.getEmail())
                .thenReturn("test@example.com");

        // =====================================================
        // ORDER
        // =====================================================

        order = mock(Order.class);

        when(order.getId())
                .thenReturn(10L);

        when(order.getUser())
                .thenReturn(customer);

        when(order.getStatus())
                .thenReturn(OrderStatus.PLACED);

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

        when(orderRepository.findByIdAndUserId(10L, 1L))
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

        return new DemoPaymentController.DemoPaymentRequest(
                outcome
        );
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
                        request(
                                DemoPaymentController.DemoOutcome.SUCCESS
                        )
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                orderRepository,
                paymentRepository
        );
    }

    // =========================================================
    // TEST 2: ORDER OWNERSHIP
    // =========================================================

    @Test
    void anotherCustomerCannotSimulatePayment() {

        when(orderRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                OrderNotFoundException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(
                                DemoPaymentController.DemoOutcome.SUCCESS
                        )
                )
        );

        verify(paymentRepository, never())
                .findByOrderId(anyLong());
    }

    // =========================================================
    // TEST 3: SUCCESSFUL DEMO PAYMENT
    // =========================================================

    @Test
    void successfulDemoDoesNotMarkPaymentAsPaid() {

        var response = controller.simulate(
                authentication(),
                10L,
                request(
                        DemoPaymentController.DemoOutcome.SUCCESS
                )
        );

        assertEquals(
                "PAID",
                response.status()
        );

        assertEquals(
                "SUCCESS",
                response.outcome()
        );

        assertEquals(
                "DEMO",
                response.provider()
        );

        assertTrue(response.demo());

        assertFalse(response.persisted());

        assertNotNull(
                response.transactionReference()
        );

        // Real payment must remain pending.

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertNull(
                payment.getTransactionReference()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    // =========================================================
    // TEST 4: FAILED DEMO PAYMENT
    // =========================================================

    @Test
    void failedDemoDoesNotModifyPayment() {

        var response = controller.simulate(
                authentication(),
                10L,
                request(
                        DemoPaymentController.DemoOutcome.FAILURE
                )
        );

        assertEquals(
                "FAILED",
                response.status()
        );

        assertEquals(
                "FAILURE",
                response.outcome()
        );

        assertTrue(response.demo());

        assertFalse(response.persisted());

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertNull(
                payment.getTransactionReference()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    // =========================================================
    // TEST 5: PAID ORDER
    // =========================================================

    @Test
    void paidOrderCannotUseDemoPayment() {

        payment.setStatus(
                PaymentStatus.PAID
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(
                                DemoPaymentController.DemoOutcome.SUCCESS
                        )
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    // =========================================================
    // TEST 6: REFUNDED ORDER
    // =========================================================

    @Test
    void refundedOrderCannotUseDemoPayment() {

        payment.setStatus(
                PaymentStatus.REFUNDED
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(
                                DemoPaymentController.DemoOutcome.SUCCESS
                        )
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    // =========================================================
    // TEST 7: TOSS PREPARED ORDER
    // =========================================================

    @Test
    void tossPreparedOrderCannotUseDemoPayment() {

        payment.setTossOrderId(
                "TOSS-TEST-123"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.simulate(
                        authentication(),
                        10L,
                        request(
                                DemoPaymentController.DemoOutcome.SUCCESS
                        )
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
                .save(any(Payment.class));
    }

    // =========================================================
    // TEST 8: REPEATED DEMO PAYMENTS
    // =========================================================

    @Test
    void repeatedDemoPaymentsDoNotPersistChanges() {

        var failed = controller.simulate(
                authentication(),
                10L,
                request(
                        DemoPaymentController.DemoOutcome.FAILURE
                )
        );

        var successful = controller.simulate(
                authentication(),
                10L,
                request(
                        DemoPaymentController.DemoOutcome.SUCCESS
                )
        );

        assertEquals(
                "FAILED",
                failed.status()
        );

        assertEquals(
                "PAID",
                successful.status()
        );

        assertNotEquals(
                failed.transactionReference(),
                successful.transactionReference()
        );

        assertFalse(failed.persisted());
        assertFalse(successful.persisted());

        // Real payment remains unchanged.

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertNull(
                payment.getTransactionReference()
        );

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }
}