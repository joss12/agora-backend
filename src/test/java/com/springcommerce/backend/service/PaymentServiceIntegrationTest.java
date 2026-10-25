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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
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

    private TestingAuthenticationToken authentication(
            String email
    ) {
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

    private void assertRealPaymentUnchanged(Long orderId) {
        Payment payment = getStoredPayment(orderId);

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertNull(payment.getTransactionReference());
        assertNull(payment.getTossOrderId());

        assertEquals(0, paymentAttemptRepository.count());
    }

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
    }

    @Test
    void successfulDemoDoesNotMarkRealPaymentAsPaid() {
        OrderResponse order = placeOrder();

        var response = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals("PAID", response.status());
        assertEquals("SUCCESS", response.outcome());
        assertEquals("DEMO", response.provider());

        assertTrue(response.demo());
        assertFalse(response.persisted());
        assertNotNull(response.transactionReference());

        assertRealPaymentUnchanged(order.id());

        assertEquals(
                OrderStatus.PLACED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void failedDemoDoesNotModifyRealPayment() {
        OrderResponse order = placeOrder();

        var response = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        assertEquals("FAILED", response.status());
        assertEquals("FAILURE", response.outcome());
        assertFalse(response.persisted());

        assertRealPaymentUnchanged(order.id());
    }

    @Test
    void failedDemoCanBeFollowedBySuccessfulDemo() {
        OrderResponse order = placeOrder();

        var failed = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        var successful = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals("FAILED", failed.status());
        assertEquals("PAID", successful.status());

        assertNotEquals(
                failed.transactionReference(),
                successful.transactionReference()
        );

        assertRealPaymentUnchanged(order.id());
    }

    @Test
    void demoDoesNotDeductStockAgain() {
        OrderResponse order = placeOrder();

        assertEquals(
                8,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        assertEquals(
                8,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );

        assertRealPaymentUnchanged(order.id());
    }

    @Test
    void demoDoesNotAuthorizeFulfillment() {
        OrderResponse order = placeOrder();

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals(
                PaymentStatus.PENDING,
                getStoredPayment(order.id()).getStatus()
        );

        assertEquals(
                OrderStatus.PLACED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void cancellingAfterDemoRestoresInventory() {
        OrderResponse order = placeOrder();

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        OrderResponse cancelled = orderService.cancelOrder(
                customer.getEmail(),
                order.id()
        );

        assertEquals("CANCELLED", cancelled.status());

        assertEquals(
                10,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );

        assertEquals(
                PaymentStatus.PENDING,
                getStoredPayment(order.id()).getStatus()
        );

        assertEquals(0, paymentAttemptRepository.count());
    }

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

        userRepository.save(otherCustomer);

        assertThrows(
                OrderNotFoundException.class,
                () -> paymentService.getPayment(
                        otherCustomer.getEmail(),
                        order.id()
                )
        );

        assertThrows(
                OrderNotFoundException.class,
                () -> demoPaymentController.simulate(
                        authentication(otherCustomer.getEmail()),
                        order.id(),
                        new DemoPaymentController.DemoPaymentRequest(
                                DemoPaymentController.DemoOutcome.SUCCESS
                        )
                )
        );

        assertRealPaymentUnchanged(order.id());
    }

    @Test
    void tossPreparedOrderCannotUseDemoPayment() {
        OrderResponse order = placeOrder();

        Payment payment = getStoredPayment(order.id());

        payment.setTossOrderId("TOSS-TEST-" + order.id());
        paymentRepository.saveAndFlush(payment);

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> simulate(
                        order.id(),
                        DemoPaymentController.DemoOutcome.SUCCESS
                )
        );

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> simulate(
                        order.id(),
                        DemoPaymentController.DemoOutcome.FAILURE
                )
        );

        Payment unchanged = getStoredPayment(order.id());

        assertEquals(
                PaymentStatus.PENDING,
                unchanged.getStatus()
        );

        assertEquals(0, paymentAttemptRepository.count());
        assertNull(unchanged.getTransactionReference());
    }
}