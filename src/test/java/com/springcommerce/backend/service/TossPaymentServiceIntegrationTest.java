package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderResponse;
import com.springcommerce.backend.dto.PaymentResponse;
import com.springcommerce.backend.dto.TossConfirmRequest;
import com.springcommerce.backend.dto.TossPrepareResponse;

import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentConflictException;
import com.springcommerce.backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TossPaymentServiceIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql =
            new MySQLContainer("mysql:8.4");

    @Autowired
    private TossPaymentService tossPaymentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @MockitoBean
    private TossPaymentClient tossClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {

        paymentAttemptRepository.deleteAll();
        paymentRepository.deleteAll();

        inventoryMovementRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        cartItemRepository.deleteAll();
        cartRepository.deleteAll();

        productRepository.deleteAll();
        userRepository.deleteAll();

        customer = new User();
        customer.setFirstName("Test");
        customer.setLastName("Customer");
        customer.setEmail("toss-test@example.com");
        customer.setPassword("test-hash");
        customer.setRole(UserRole.CUSTOMER);
        customer.setEnabled(true);

        customer = userRepository.save(customer);

        product = new Product();
        product.setName("Test Keyboard");
        product.setSku("TOSS-KB-001");
        product.setPrice(new BigDecimal("10000.00"));
        product.setStock(10);
        product.setActive(true);

        product = productRepository.save(product);
    }

    private CreateOrderRequest deliveryAddress() {

        return new CreateOrderRequest(
                "Kim Minjun",
                "010-1234-5678",
                "06236",
                "123 Teheran-ro, Gangnam-gu, Seoul",
                "Apartment 502",
                "Leave at the door"
        );
    }

    private OrderResponse placeOrder() {

        cartService.addItem(
                customer.getEmail(),
                new AddCartItemRequest(
                        product.getId(),
                        2
                )
        );

        return orderService.checkout(
                customer.getEmail(),
                deliveryAddress()
        );
    }

    private TossPrepareResponse prepareOrder(Long orderId) {

        return tossPaymentService.prepare(
                customer.getEmail(),
                orderId
        );
    }

    private TossConfirmRequest confirmationRequest(
            TossPrepareResponse prepared,
            String paymentKey
    ) {

        return new TossConfirmRequest(
                paymentKey,
                prepared.tossOrderId(),
                prepared.amount()
        );
    }

    private void mockSuccessfulConfirmation(
            TossPrepareResponse prepared,
            String paymentKey
    ) {

        when(tossClient.confirm(
                eq(paymentKey),
                eq(prepared.tossOrderId()),
                eq(prepared.amount()),
                anyString()
        )).thenReturn(
                new TossPaymentClient.TossPaymentResult(
                        paymentKey,
                        prepared.tossOrderId(),
                        prepared.amount(),
                        "DONE"
                )
        );
    }

    // =========================================================
    // TEST 1: PREPARE PAYMENT
    // =========================================================

    @Test
    void preparePaymentCreatesTossOrderId() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        assertEquals(order.id(), prepared.orderId());
        assertEquals(20000L, prepared.amount());
        assertEquals("KRW", prepared.currency());

        assertNotNull(prepared.tossOrderId());

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                prepared.tossOrderId(),
                payment.getTossOrderId()
        );

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        verifyNoInteractions(tossClient);
    }

    // =========================================================
    // TEST 2: PREPARE IDEMPOTENCY
    // =========================================================

    @Test
    void preparingSameOrderTwiceReusesTossOrderId() {

        OrderResponse order = placeOrder();

        TossPrepareResponse first =
                prepareOrder(order.id());

        TossPrepareResponse second =
                prepareOrder(order.id());

        assertEquals(
                first.tossOrderId(),
                second.tossOrderId()
        );

        assertEquals(first.amount(), second.amount());

        verifyNoInteractions(tossClient);
    }

    // =========================================================
    // TEST 3: SUCCESSFUL CONFIRMATION
    // =========================================================

    @Test
    void successfulConfirmationMarksPaymentAsPaid() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        String paymentKey = "test-payment-key-001";

        mockSuccessfulConfirmation(prepared, paymentKey);

        PaymentResponse response = tossPaymentService.confirm(
                customer.getEmail(),
                order.id(),
                confirmationRequest(prepared, paymentKey)
        );

        assertEquals("PAID", response.status());
        assertEquals(paymentKey, response.transactionReference());

        assertEquals(1, response.attempts().size());

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(PaymentStatus.PAID, payment.getStatus());

        assertEquals(
                paymentKey,
                payment.getTransactionReference()
        );

        assertEquals(1, paymentAttemptRepository.count());

        // Payment does not automatically advance fulfillment.
        assertEquals(
                OrderStatus.PLACED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );

        // Checkout reserved two items; confirmation must
        // not deduct inventory again.
        assertEquals(
                8,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );

        verify(tossClient, times(1)).confirm(
                eq(paymentKey),
                eq(prepared.tossOrderId()),
                eq(prepared.amount()),
                anyString()
        );
    }

    // =========================================================
    // TEST 4: DUPLICATE CONFIRMATION
    // =========================================================

    @Test
    void duplicateConfirmationDoesNotChargeTwice() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        String paymentKey = "test-payment-key-002";

        mockSuccessfulConfirmation(prepared, paymentKey);

        TossConfirmRequest request =
                confirmationRequest(prepared, paymentKey);

        PaymentResponse first = tossPaymentService.confirm(
                customer.getEmail(),
                order.id(),
                request
        );

        PaymentResponse second = tossPaymentService.confirm(
                customer.getEmail(),
                order.id(),
                request
        );

        assertEquals("PAID", first.status());
        assertEquals("PAID", second.status());

        assertEquals(
                first.transactionReference(),
                second.transactionReference()
        );

        assertEquals(1, paymentAttemptRepository.count());

        verify(tossClient, times(1)).confirm(
                eq(paymentKey),
                eq(prepared.tossOrderId()),
                eq(prepared.amount()),
                anyString()
        );
    }

    // =========================================================
    // TEST 5: INVALID REQUEST AMOUNT
    // =========================================================

    @Test
    void incorrectAmountIsRejectedBeforeContactingToss() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        TossConfirmRequest invalidRequest =
                new TossConfirmRequest(
                        "test-payment-key-003",
                        prepared.tossOrderId(),
                        prepared.amount() + 1000
                );

        assertThrows(
                PaymentConflictException.class,
                () -> tossPaymentService.confirm(
                        customer.getEmail(),
                        order.id(),
                        invalidRequest
                )
        );

        assertEquals(
                PaymentStatus.PENDING,
                paymentRepository.findByOrderId(order.id())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(0, paymentAttemptRepository.count());

        verifyNoInteractions(tossClient);
    }

    // =========================================================
    // TEST 6: INCORRECT TOSS ORDER ID
    // =========================================================

    @Test
    void incorrectTossOrderIdIsRejected() {

        OrderResponse order = placeOrder();

        prepareOrder(order.id());

        TossConfirmRequest invalidRequest =
                new TossConfirmRequest(
                        "test-payment-key-004",
                        "WRONG-TOSS-ORDER-ID",
                        20000L
                );

        assertThrows(
                PaymentConflictException.class,
                () -> tossPaymentService.confirm(
                        customer.getEmail(),
                        order.id(),
                        invalidRequest
                )
        );

        assertEquals(0, paymentAttemptRepository.count());

        verifyNoInteractions(tossClient);
    }

    // =========================================================
    // TEST 7: TOSS RETURNS WRONG AMOUNT
    // =========================================================

    @Test
    void unexpectedAmountFromTossIsRejected() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        String paymentKey = "test-payment-key-005";

        when(tossClient.confirm(
                eq(paymentKey),
                eq(prepared.tossOrderId()),
                eq(prepared.amount()),
                anyString()
        )).thenReturn(
                new TossPaymentClient.TossPaymentResult(
                        paymentKey,
                        prepared.tossOrderId(),
                        prepared.amount() + 1000,
                        "DONE"
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tossPaymentService.confirm(
                        customer.getEmail(),
                        order.id(),
                        confirmationRequest(prepared, paymentKey)
                )
        );

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                exception.getStatusCode()
        );

        assertEquals(
                PaymentStatus.PENDING,
                paymentRepository.findByOrderId(order.id())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(0, paymentAttemptRepository.count());
    }

    // =========================================================
    // TEST 8: TOSS RETURNS NON-DONE STATUS
    // =========================================================

    @Test
    void incompleteTossPaymentCannotBeMarkedPaid() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        String paymentKey = "test-payment-key-006";

        when(tossClient.confirm(
                eq(paymentKey),
                eq(prepared.tossOrderId()),
                eq(prepared.amount()),
                anyString()
        )).thenReturn(
                new TossPaymentClient.TossPaymentResult(
                        paymentKey,
                        prepared.tossOrderId(),
                        prepared.amount(),
                        "IN_PROGRESS"
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tossPaymentService.confirm(
                        customer.getEmail(),
                        order.id(),
                        confirmationRequest(prepared, paymentKey)
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                PaymentStatus.PENDING,
                paymentRepository.findByOrderId(order.id())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(0, paymentAttemptRepository.count());
    }

    // =========================================================
    // TEST 9: UNAUTHORIZED CUSTOMER
    // =========================================================

    @Test
    void anotherCustomerCannotConfirmPayment() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        User otherCustomer = new User();
        otherCustomer.setFirstName("Other");
        otherCustomer.setLastName("Customer");
        otherCustomer.setEmail("other-toss-test@example.com");
        otherCustomer.setPassword("test-hash");
        otherCustomer.setRole(UserRole.CUSTOMER);
        otherCustomer.setEnabled(true);

        userRepository.save(otherCustomer);

        assertThrows(
                OrderNotFoundException.class,
                () -> tossPaymentService.confirm(
                        otherCustomer.getEmail(),
                        order.id(),
                        confirmationRequest(
                                prepared,
                                "test-payment-key-007"
                        )
                )
        );

        assertEquals(0, paymentAttemptRepository.count());

        verifyNoInteractions(tossClient);
    }

    // =========================================================
    // TEST 10: CANCELLED ORDER
    // =========================================================

    @Test
    void cancelledOrderCannotBeConfirmed() {

        OrderResponse order = placeOrder();

        // Cancel before preparing Toss.
        orderService.cancelOrder(
                customer.getEmail(),
                order.id()
        );

        TossConfirmRequest request =
                new TossConfirmRequest(
                        "test-payment-key-008",
                        "NONEXISTENT-TOSS-ORDER",
                        20000L
                );

        assertThrows(
                PaymentConflictException.class,
                () -> tossPaymentService.confirm(
                        customer.getEmail(),
                        order.id(),
                        request
                )
        );

        assertEquals(0, paymentAttemptRepository.count());

        verifyNoInteractions(tossClient);
    }

    // =========================================================
    // TEST 11: DIFFERENT PAYMENT KEY
    // =========================================================

    @Test
    void paidOrderRejectsDifferentPaymentKey() {

        OrderResponse order = placeOrder();

        TossPrepareResponse prepared =
                prepareOrder(order.id());

        String originalPaymentKey = "test-payment-key-009";

        mockSuccessfulConfirmation(
                prepared,
                originalPaymentKey
        );

        tossPaymentService.confirm(
                customer.getEmail(),
                order.id(),
                confirmationRequest(
                        prepared,
                        originalPaymentKey
                )
        );

        assertThrows(
                PaymentConflictException.class,
                () -> tossPaymentService.confirm(
                        customer.getEmail(),
                        order.id(),
                        confirmationRequest(
                                prepared,
                                "different-payment-key"
                        )
                )
        );

        assertEquals(1, paymentAttemptRepository.count());

        verify(tossClient, times(1)).confirm(
                anyString(),
                anyString(),
                anyLong(),
                anyString()
        );
    }
}