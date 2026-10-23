
package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderResponse;
import com.springcommerce.backend.dto.PaymentResponse;

import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentConflictException;
import com.springcommerce.backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

    // =========================================================
    // TEST 1: SUCCESSFUL PAYMENT
    // =========================================================

    @Test
    void successfulPaymentMarksPaymentAsPaid() {

        OrderResponse order = placeOrder();

        PaymentResponse response = paymentService.simulate(
                customer.getEmail(),
                order.id(),
                PaymentOutcome.SUCCESS
        );

        assertEquals("PAID", response.status());

        assertNotNull(response.transactionReference());

        assertEquals(1, response.attempts().size());

        assertEquals(
                1,
                paymentAttemptRepository.count()
        );

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PAID,
                payment.getStatus()
        );

        assertEquals(
                0,
                new BigDecimal("20000.00")
                        .compareTo(payment.getAmount())
        );

        // Paying does not advance fulfillment automatically.
        assertEquals(
                OrderStatus.PLACED,
                orderRepository.findById(order.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    // =========================================================
    // TEST 2: FAILED PAYMENT
    // =========================================================

    @Test
    void failedPaymentRecordsAttemptAndAllowsRetry() {

        OrderResponse order = placeOrder();

        PaymentResponse response = paymentService.simulate(
                customer.getEmail(),
                order.id(),
                PaymentOutcome.FAILURE
        );

        assertEquals("FAILED", response.status());

        assertEquals(
                1,
                response.attempts().size()
        );

        assertEquals(
                "FAILURE",
                response.attempts().get(0).outcome()
        );

        assertEquals(
                PaymentStatus.FAILED,
                paymentRepository.findByOrderId(order.id())
                        .orElseThrow()
                        .getStatus()
        );

        // A declined payment must not restore stock.
        assertEquals(
                8,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );
    }

    // =========================================================
    // TEST 3: RETRY FAILED PAYMENT
    // =========================================================

    @Test
    void failedPaymentCanBeRetriedSuccessfully() {

        OrderResponse order = placeOrder();

        paymentService.simulate(
                customer.getEmail(),
                order.id(),
                PaymentOutcome.FAILURE
        );

        PaymentResponse response = paymentService.simulate(
                customer.getEmail(),
                order.id(),
                PaymentOutcome.SUCCESS
        );

        assertEquals("PAID", response.status());

        assertEquals(
                2,
                response.attempts().size()
        );

        assertEquals(
                "FAILURE",
                response.attempts().get(0).outcome()
        );

        assertEquals(
                "SUCCESS",
                response.attempts().get(1).outcome()
        );

        assertEquals(
                2,
                paymentAttemptRepository.count()
        );

        // Retrying payment must not deduct stock again.
        assertEquals(
                8,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );
    }

    // =========================================================
    // TEST 4: DUPLICATE SUCCESSFUL PAYMENT
    // =========================================================

    @Test
    void paidOrderCannotBePaidAgain() {

        OrderResponse order = placeOrder();

        paymentService.simulate(
                customer.getEmail(),
                order.id(),
                PaymentOutcome.SUCCESS
        );

        assertThrows(
                PaymentConflictException.class,
                () -> paymentService.simulate(
                        customer.getEmail(),
                        order.id(),
                        PaymentOutcome.SUCCESS
                )
        );

        assertEquals(
                1,
                paymentAttemptRepository.count()
        );
    }

    // =========================================================
    // TEST 5: REFUND ON CANCELLATION
    // =========================================================

    @Test
    void cancellingPaidOrderSimulatesRefund() {

        OrderResponse order = placeOrder();

        paymentService.simulate(
                customer.getEmail(),
                order.id(),
                PaymentOutcome.SUCCESS
        );

        OrderResponse cancelled = orderService.cancelOrder(
                customer.getEmail(),
                order.id()
        );

        assertEquals(
                "CANCELLED",
                cancelled.status()
        );

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );

        // Inventory must be restored.
        assertEquals(
                10,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );

        // The original payment attempt is retained.
        assertEquals(
                1,
                paymentAttemptRepository.count()
        );
    }

    // =========================================================
    // TEST 6: ORDER OWNERSHIP
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
                () -> paymentService.simulate(
                        otherCustomer.getEmail(),
                        order.id(),
                        PaymentOutcome.SUCCESS
                )
        );

        assertEquals(
                PaymentStatus.PENDING,
                paymentRepository.findByOrderId(order.id())
                        .orElseThrow()
                        .getStatus()
        );

        assertEquals(
                0,
                paymentAttemptRepository.count()
        );
    }
    @Test
    void tossPreparedOrderCannotUseSimulatedPayment() {

        OrderResponse order = placeOrder();

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        payment.setTossOrderId("TOSS-TEST-" + order.id());
        paymentRepository.saveAndFlush(payment);

        assertThrows(
                PaymentConflictException.class,
                () -> paymentService.simulate(
                        customer.getEmail(),
                        order.id(),
                        PaymentOutcome.SUCCESS
                )
        );

        assertThrows(
                PaymentConflictException.class,
                () -> paymentService.simulate(
                        customer.getEmail(),
                        order.id(),
                        PaymentOutcome.FAILURE
                )
        );

        Payment unchangedPayment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                unchangedPayment.getStatus()
        );

        assertEquals(0, paymentAttemptRepository.count());

        assertNull(unchangedPayment.getTransactionReference());
    }
}
