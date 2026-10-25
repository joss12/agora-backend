package com.springcommerce.backend.service;

import com.springcommerce.backend.controller.DemoPaymentController;
import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderResponse;
import com.springcommerce.backend.dto.PaymentResponse;
import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = "app.payment.demo-enabled=true")
class PaymentServiceIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql =
            new MySQLContainer("mysql:8.4");

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DemoPaymentController demoPaymentController;

    @Autowired
    private CartService cartService;

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

    // =========================================================
    // SETUP
    // =========================================================

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
        customer.setEmail("payment-test@example.com");
        customer.setPassword("test-hash");
        customer.setRole(UserRole.CUSTOMER);
        customer.setEnabled(true);

        customer = userRepository.save(customer);

        product = new Product();
        product.setName("Test Keyboard");
        product.setSku("PAYMENT-KB-001");
        product.setPrice(new BigDecimal("10000.00"));
        product.setStock(10);
        product.setActive(true);

        product = productRepository.save(product);
    }

    // =========================================================
    // HELPERS
    // =========================================================

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

    private TestingAuthenticationToken authentication(String email) {

        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        email,
                        null,
                        "ROLE_CUSTOMER"
                );

        authentication.setAuthenticated(true);

        return authentication;
    }

    private DemoPaymentController.DemoPaymentResponse simulate(
            Long orderId,
            DemoPaymentController.DemoOutcome outcome
    ) {

        return demoPaymentController.simulate(
                authentication(customer.getEmail()),
                orderId,
                new DemoPaymentController.DemoPaymentRequest(outcome)
        );
    }

    private Payment getStoredPayment(Long orderId) {

        return paymentRepository
                .findByOrderId(orderId)
                .orElseThrow();
    }

    private void assertPendingPayment(Long orderId) {

        Payment payment = getStoredPayment(orderId);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getTransactionReference());
        assertNull(payment.getTossOrderId());

        assertEquals(0, paymentAttemptRepository.count());
    }

    private int currentStock() {

        return productRepository
                .findById(product.getId())
                .orElseThrow()
                .getStock();
    }

    // =========================================================
    // TEST 1: CHECKOUT CREATES PENDING PAYMENT
    // =========================================================

    @Test
    void checkoutCreatesPendingPayment() {

        OrderResponse order = placeOrder();

        PaymentResponse response = paymentService.getPayment(
                customer.getEmail(),
                order.id()
        );

        assertEquals("PENDING", response.status());

        assertEquals(
                0,
                new BigDecimal("20000.00")
                        .compareTo(response.amount())
        );

        assertTrue(response.attempts().isEmpty());
        assertNull(response.transactionReference());

        assertEquals(
                OrderStatus.PLACED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(8, currentStock());

        assertPendingPayment(order.id());
    }

    // =========================================================
    // TEST 2: SUCCESSFUL DEMO PAYMENT
    // =========================================================

    @Test
    void successfulDemoMarksPaymentAsPaid() {

        OrderResponse order = placeOrder();

        var response = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
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

        Payment payment = getStoredPayment(order.id());

        assertEquals(PaymentStatus.PAID, payment.getStatus());

        assertEquals(
                response.transactionReference(),
                payment.getTransactionReference()
        );

        assertNull(payment.getTossOrderId());

        assertEquals(1, paymentAttemptRepository.count());

        // Payment simulation must not deduct stock again.
        assertEquals(8, currentStock());

        // The payment controller does not change order status.
        assertEquals(
                OrderStatus.PLACED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    // =========================================================
    // TEST 3: FAILED DEMO PAYMENT
    // =========================================================

    @Test
    void failedDemoMarksPaymentAsFailed() {

        OrderResponse order = placeOrder();

        var response = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        assertEquals("FAILED", response.status());
        assertEquals("FAILURE", response.outcome());
        assertEquals("DEMO", response.provider());

        assertTrue(response.demo());
        assertTrue(response.persisted());

        assertNotNull(response.transactionReference());

        Payment payment = getStoredPayment(order.id());

        assertEquals(PaymentStatus.FAILED, payment.getStatus());

        assertNull(payment.getTransactionReference());
        assertNull(payment.getTossOrderId());

        assertEquals(1, paymentAttemptRepository.count());

        assertEquals(8, currentStock());
    }

    // =========================================================
    // TEST 4: FAILED DEMO CAN BE RETRIED
    // =========================================================

    @Test
    void failedDemoCanBeFollowedBySuccessfulDemo() {

        OrderResponse order = placeOrder();

        var failed = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        assertEquals("FAILED", failed.status());

        assertEquals(
                PaymentStatus.FAILED,
                getStoredPayment(order.id()).getStatus()
        );

        var successful = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals("PAID", successful.status());

        assertNotEquals(
                failed.transactionReference(),
                successful.transactionReference()
        );

        assertTrue(failed.persisted());
        assertTrue(successful.persisted());

        Payment payment = getStoredPayment(order.id());

        assertEquals(PaymentStatus.PAID, payment.getStatus());

        assertEquals(
                successful.transactionReference(),
                payment.getTransactionReference()
        );

        assertEquals(2, paymentAttemptRepository.count());

        assertEquals(8, currentStock());
    }

    // =========================================================
    // TEST 5: DEMO DOES NOT DEDUCT STOCK AGAIN
    // =========================================================

    @Test
    void demoDoesNotDeductStockAgain() {

        OrderResponse order = placeOrder();

        assertEquals(8, currentStock());

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        assertEquals(8, currentStock());

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals(8, currentStock());

        assertEquals(
                PaymentStatus.PAID,
                getStoredPayment(order.id()).getStatus()
        );

        assertEquals(2, paymentAttemptRepository.count());
    }

    // =========================================================
    // TEST 6: SUCCESSFUL DEMO DOES NOT CHANGE ORDER STATUS
    // =========================================================

    @Test
    void demoDoesNotAutomaticallyChangeOrderStatus() {

        OrderResponse order = placeOrder();

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals(
                PaymentStatus.PAID,
                getStoredPayment(order.id()).getStatus()
        );

        assertEquals(
                OrderStatus.PLACED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    // =========================================================
    // TEST 7: CANCELLATION AFTER SUCCESSFUL DEMO
    // =========================================================

    @Test
    void cancellingAfterSuccessfulDemoRestoresInventory() {

        OrderResponse order = placeOrder();

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals(
                PaymentStatus.PAID,
                getStoredPayment(order.id()).getStatus()
        );

        assertEquals(8, currentStock());

        OrderResponse cancelled = orderService.cancelOrder(
                customer.getEmail(),
                order.id()
        );

        assertEquals("CANCELLED", cancelled.status());

        // The original reserved stock is restored.
        assertEquals(10, currentStock());

        // A locally paid demo payment is resolved as refunded.
        assertEquals(
                PaymentStatus.REFUNDED,
                getStoredPayment(order.id()).getStatus()
        );

        // Cancellation does not erase payment history.
        assertEquals(1, paymentAttemptRepository.count());

        assertEquals(
                OrderStatus.CANCELLED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    // =========================================================
    // TEST 8: ORDER OWNERSHIP
    // =========================================================

    @Test
    void anotherCustomerCannotAccessPayment() {

        OrderResponse order = placeOrder();

        User otherCustomer = new User();
        otherCustomer.setFirstName("Other");
        otherCustomer.setLastName("Customer");
        otherCustomer.setEmail("other-payment@example.com");
        otherCustomer.setPassword("test-hash");
        otherCustomer.setRole(UserRole.CUSTOMER);
        otherCustomer.setEnabled(true);

        otherCustomer = userRepository.save(otherCustomer);

        String otherEmail = otherCustomer.getEmail();

        assertThrows(
                OrderNotFoundException.class,
                () -> paymentService.getPayment(
                        otherEmail,
                        order.id()
                )
        );

        assertThrows(
                OrderNotFoundException.class,
                () -> demoPaymentController.simulate(
                        authentication(otherEmail),
                        order.id(),
                        new DemoPaymentController.DemoPaymentRequest(
                                DemoPaymentController.DemoOutcome.SUCCESS
                        )
                )
        );

        assertPendingPayment(order.id());
    }

    // =========================================================
    // TEST 9: TOSS PREPARED ORDER
    // =========================================================

    @Test
    void tossPreparedOrderCannotUseDemoPayment() {

        OrderResponse order = placeOrder();

        Payment payment = getStoredPayment(order.id());

        payment.setTossOrderId("TOSS-TEST-" + order.id());

        paymentRepository.saveAndFlush(payment);

        ResponseStatusException successException = assertThrows(
                ResponseStatusException.class,
                () -> simulate(
                        order.id(),
                        DemoPaymentController.DemoOutcome.SUCCESS
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                successException.getStatusCode()
        );

        ResponseStatusException failureException = assertThrows(
                ResponseStatusException.class,
                () -> simulate(
                        order.id(),
                        DemoPaymentController.DemoOutcome.FAILURE
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                failureException.getStatusCode()
        );

        Payment unchanged = getStoredPayment(order.id());

        assertEquals(PaymentStatus.PENDING, unchanged.getStatus());
        assertNull(unchanged.getTransactionReference());

        assertEquals(0, paymentAttemptRepository.count());
    }

    // =========================================================
    // TEST 10: SUCCESSFUL DEMO CANNOT BE REPEATED
    // =========================================================

    @Test
    void successfulDemoCannotBeRepeated() {

        OrderResponse order = placeOrder();

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> simulate(
                        order.id(),
                        DemoPaymentController.DemoOutcome.SUCCESS
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                PaymentStatus.PAID,
                getStoredPayment(order.id()).getStatus()
        );

        assertEquals(1, paymentAttemptRepository.count());
    }
}