package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderResponse;

import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.exception.InsufficientStockException;
import com.springcommerce.backend.exception.ProductNotFoundException;
import com.springcommerce.backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql =
            new MySQLContainer("mysql:8.4");

    // ============================================================
    // SERVICES
    // ============================================================

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    // ============================================================
    // REPOSITORIES
    // ============================================================

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private InventoryMovementRepository inventoryMovementRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    // ============================================================
    // TEST DATA
    // ============================================================

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
        customer.setEmail("checkout-test@example.com");
        customer.setPassword("test-hash");
        customer.setRole(UserRole.CUSTOMER);
        customer.setEnabled(true);

        customer = userRepository.save(customer);

        product = new Product();

        product.setName("Test Keyboard");
        product.setSku("TEST-KB-001");
        product.setPrice(new BigDecimal("10000.00"));
        product.setStock(10);
        product.setActive(true);

        product = productRepository.save(product);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private CreateOrderRequest testDeliveryAddress() {

        return new CreateOrderRequest(
                "Kim Minjun",
                "010-1234-5678",
                "06236",
                "123 Teheran-ro, Gangnam-gu, Seoul",
                "Apartment 502",
                "Leave at the door"
        );
    }

    private OrderResponse checkoutProduct(int quantity) {

        cartService.addItem(
                customer.getEmail(),
                new AddCartItemRequest(
                        product.getId(),
                        quantity
                )
        );

        return orderService.checkout(
                customer.getEmail(),
                testDeliveryAddress()
        );
    }

    private Payment prepareTossPayment(
            Long orderId,
            PaymentStatus status
    ) {

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow();

        payment.setTossOrderId("TOSS-TEST-" + orderId);
        payment.setStatus(status);

        if (status == PaymentStatus.PAID) {
            payment.setTransactionReference(
                    "PAYMENT-KEY-" + orderId
            );
        }

        return paymentRepository.saveAndFlush(payment);
    }

    private TossPaymentClient.TossPaymentResult tossResult(
            String paymentKey,
            String tossOrderId,
            long amount,
            String status,
            Long balanceAmount
    ) {

        return new TossPaymentClient.TossPaymentResult(
                paymentKey,
                tossOrderId,
                amount,
                status,
                balanceAmount
        );
    }

    private void assertStock(int expectedStock) {

        assertEquals(
                expectedStock,
                productRepository
                        .findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );
    }

    private void assertOrderStatus(
            Long orderId,
            OrderStatus expectedStatus
    ) {

        assertEquals(
                expectedStatus,
                orderRepository
                        .findById(orderId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    private void assertPaymentStatus(
            Long orderId,
            PaymentStatus expectedStatus
    ) {

        assertEquals(
                expectedStatus,
                paymentRepository
                        .findByOrderId(orderId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    // ============================================================
    // TEST 1: SUCCESSFUL CHECKOUT
    // ============================================================

    @Test
    void checkoutCreatesOrderAndDeductsStock() {

        OrderResponse response = checkoutProduct(3);

        assertNotNull(response.id());

        assertEquals(
                "PLACED",
                response.status()
        );

        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(response.totalAmount())
        );

        assertEquals(1, response.items().size());

        assertEquals(
                "Kim Minjun",
                response.recipientName()
        );

        assertEquals(
                "010-1234-5678",
                response.phoneNumber()
        );

        assertEquals(
                "06236",
                response.postalCode()
        );

        assertEquals(
                "123 Teheran-ro, Gangnam-gu, Seoul",
                response.address()
        );

        assertEquals(
                "Apartment 502",
                response.addressDetail()
        );

        assertEquals(
                "Leave at the door",
                response.deliveryInstructions()
        );

        Order savedOrder = orderRepository
                .findById(response.id())
                .orElseThrow();

        assertEquals(
                "Kim Minjun",
                savedOrder.getRecipientName()
        );

        assertEquals(
                "06236",
                savedOrder.getPostalCode()
        );

        assertEquals(
                "123 Teheran-ro, Gangnam-gu, Seoul",
                savedOrder.getAddress()
        );

        assertStock(7);

        assertEquals(1, orderRepository.count());
        assertEquals(1, orderItemRepository.count());
        assertEquals(1, inventoryMovementRepository.count());
        assertEquals(1, paymentRepository.count());

        Payment payment = paymentRepository
                .findByOrderId(response.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(payment.getAmount())
        );

        assertNull(payment.getTransactionReference());

        assertEquals(
                0,
                paymentAttemptRepository.count()
        );

        Long cartId = cartRepository
                .findByUserId(customer.getId())
                .orElseThrow()
                .getId();

        assertTrue(
                cartItemRepository
                        .findByCartIdOrderByCreatedAtAsc(cartId)
                        .isEmpty()
        );
    }

    // ============================================================
    // TEST 2: INSUFFICIENT STOCK
    // ============================================================

    @Test
    void insufficientStockRollsBackCheckout() {

        cartService.addItem(
                customer.getEmail(),
                new AddCartItemRequest(
                        product.getId(),
                        5
                )
        );

        product.setStock(2);
        productRepository.saveAndFlush(product);

        assertThrows(
                InsufficientStockException.class,
                () -> orderService.checkout(
                        customer.getEmail(),
                        testDeliveryAddress()
                )
        );

        assertEquals(0, orderRepository.count());
        assertEquals(0, orderItemRepository.count());
        assertEquals(0, paymentRepository.count());
        assertEquals(0, paymentAttemptRepository.count());

        assertStock(2);

        assertEquals(
                0,
                inventoryMovementRepository.count()
        );

        Long cartId = cartRepository
                .findByUserId(customer.getId())
                .orElseThrow()
                .getId();

        assertEquals(
                1,
                cartItemRepository
                        .findByCartIdOrderByCreatedAtAsc(cartId)
                        .size()
        );
    }

    // ============================================================
    // TEST 3: ARCHIVED PRODUCT
    // ============================================================

    @Test
    void archivedProductCannotBePurchased() {

        cartService.addItem(
                customer.getEmail(),
                new AddCartItemRequest(
                        product.getId(),
                        2
                )
        );

        product.setActive(false);
        productRepository.saveAndFlush(product);

        assertThrows(
                ProductNotFoundException.class,
                () -> orderService.checkout(
                        customer.getEmail(),
                        testDeliveryAddress()
                )
        );

        assertEquals(0, orderRepository.count());
        assertEquals(0, orderItemRepository.count());
        assertEquals(0, paymentRepository.count());
        assertEquals(0, paymentAttemptRepository.count());

        assertStock(10);

        assertEquals(
                0,
                inventoryMovementRepository.count()
        );

        Long cartId = cartRepository
                .findByUserId(customer.getId())
                .orElseThrow()
                .getId();

        assertEquals(
                1,
                cartItemRepository
                        .findByCartIdOrderByCreatedAtAsc(cartId)
                        .size()
        );
    }

    // ============================================================
    // TEST 4: CANCEL UNPAID ORDER
    // ============================================================

    @Test
    void cancellingUnpaidOrderRestoresStock() {

        OrderResponse placedOrder = checkoutProduct(3);

        OrderResponse cancelledOrder =
                orderService.cancelOrder(
                        customer.getEmail(),
                        placedOrder.id()
                );

        assertEquals(
                "CANCELLED",
                cancelledOrder.status()
        );

        assertStock(10);

        assertEquals(
                2,
                inventoryMovementRepository.count()
        );

        assertEquals(1, orderRepository.count());
        assertEquals(1, orderItemRepository.count());

        assertPaymentStatus(
                placedOrder.id(),
                PaymentStatus.PENDING
        );

        assertEquals(
                0,
                paymentAttemptRepository.count()
        );

        verify(
                tossPaymentClient,
                never()
        ).cancelPayment(
                anyString(),
                anyString(),
                anyString()
        );
    }

    // ============================================================
    // TEST 5: TOSS READY PAYMENT REMAINS PROTECTED
    // ============================================================

    @Test
    void customerCannotCancelTossPreparedOrder() {

        OrderResponse order = checkoutProduct(2);

        Payment payment = prepareTossPayment(
                order.id(),
                PaymentStatus.PENDING
        );

        String tossOrderId = payment.getTossOrderId();

        when(
                tossPaymentClient.getPaymentByOrderId(
                        tossOrderId
                )
        ).thenReturn(
                tossResult(
                        null,
                        tossOrderId,
                        20000L,
                        "READY",
                        20000L
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(
                        customer.getEmail(),
                        order.id()
                )
        );

        assertEquals(
                HttpStatus.CONFLICT.value(),
                exception.getStatusCode().value()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PLACED
        );

        assertPaymentStatus(
                order.id(),
                PaymentStatus.PENDING
        );

        assertStock(8);

        assertEquals(
                1,
                inventoryMovementRepository.count()
        );

        verify(tossPaymentClient)
                .getPaymentByOrderId(tossOrderId);

        verify(
                tossPaymentClient,
                never()
        ).cancelPayment(
                anyString(),
                anyString(),
                anyString()
        );
    }

    // ============================================================
    // TEST 6: SUCCESSFUL FULL TOSS REFUND
    // ============================================================

    @Test
    void cancellingPaidOrderRefundsTossAndRestoresStock() {

        OrderResponse order = checkoutProduct(2);

        Payment payment = prepareTossPayment(
                order.id(),
                PaymentStatus.PAID
        );

        String tossOrderId = payment.getTossOrderId();
        String paymentKey = payment.getTransactionReference();

        // Toss reports that the payment was completed.

        when(
                tossPaymentClient.getPaymentByOrderId(
                        tossOrderId
                )
        ).thenReturn(
                tossResult(
                        paymentKey,
                        tossOrderId,
                        20000L,
                        "DONE",
                        20000L
                )
        );

        // Toss confirms a successful full refund.

        when(
                tossPaymentClient.cancelPayment(
                        eq(paymentKey),
                        anyString(),
                        anyString()
                )
        ).thenReturn(
                tossResult(
                        paymentKey,
                        tossOrderId,
                        20000L,
                        "CANCELED",
                        0L
                )
        );

        OrderResponse cancelled = orderService.cancelOrder(
                customer.getEmail(),
                order.id()
        );

        assertEquals(
                "CANCELLED",
                cancelled.status()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertPaymentStatus(
                order.id(),
                PaymentStatus.REFUNDED
        );

        assertStock(10);

        assertEquals(
                2,
                inventoryMovementRepository.count()
        );

        assertEquals(1, orderRepository.count());
        assertEquals(1, orderItemRepository.count());

        verify(
                tossPaymentClient,
                times(1)
        ).cancelPayment(
                eq(paymentKey),
                anyString(),
                anyString()
        );
    }

    // ============================================================
    // TEST 7: REFUND FAILURE PRESERVES ORDER AND INVENTORY
    // ============================================================

    @Test
    void failedTossRefundDoesNotCancelOrder() {

        OrderResponse order = checkoutProduct(2);

        Payment payment = prepareTossPayment(
                order.id(),
                PaymentStatus.PAID
        );

        String tossOrderId = payment.getTossOrderId();
        String paymentKey = payment.getTransactionReference();

        when(
                tossPaymentClient.getPaymentByOrderId(
                        tossOrderId
                )
        ).thenReturn(
                tossResult(
                        paymentKey,
                        tossOrderId,
                        20000L,
                        "DONE",
                        20000L
                )
        );

        // Simulate a network failure during the refund.

        when(
                tossPaymentClient.cancelPayment(
                        eq(paymentKey),
                        anyString(),
                        anyString()
                )
        ).thenThrow(
                new RestClientException(
                        "Simulated Toss refund network failure"
                )
        );

        // Reconciliation confirms that Toss still reports DONE.

        when(
                tossPaymentClient.getPayment(paymentKey)
        ).thenReturn(
                tossResult(
                        paymentKey,
                        tossOrderId,
                        20000L,
                        "DONE",
                        20000L
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(
                        customer.getEmail(),
                        order.id()
                )
        );

        assertEquals(
                HttpStatus.BAD_GATEWAY.value(),
                exception.getStatusCode().value()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PLACED
        );

        assertPaymentStatus(
                order.id(),
                PaymentStatus.PAID
        );

        // Reserved inventory must not be released.

        assertStock(8);

        assertEquals(
                1,
                inventoryMovementRepository.count()
        );

        verify(
                tossPaymentClient,
                times(1)
        ).cancelPayment(
                eq(paymentKey),
                anyString(),
                anyString()
        );

        verify(tossPaymentClient)
                .getPayment(paymentKey);
    }

    // ============================================================
    // TEST 8: DUPLICATE CANCELLATION CANNOT REFUND TWICE
    // ============================================================

    @Test
    void duplicateCancellationDoesNotRefundTwice() {

        OrderResponse order = checkoutProduct(2);

        Payment payment = prepareTossPayment(
                order.id(),
                PaymentStatus.PAID
        );

        String tossOrderId = payment.getTossOrderId();
        String paymentKey = payment.getTransactionReference();

        when(
                tossPaymentClient.getPaymentByOrderId(
                        tossOrderId
                )
        ).thenReturn(
                tossResult(
                        paymentKey,
                        tossOrderId,
                        20000L,
                        "DONE",
                        20000L
                )
        );

        when(
                tossPaymentClient.cancelPayment(
                        eq(paymentKey),
                        anyString(),
                        anyString()
                )
        ).thenReturn(
                tossResult(
                        paymentKey,
                        tossOrderId,
                        20000L,
                        "CANCELED",
                        0L
                )
        );

        // First cancellation succeeds.

        OrderResponse cancelled = orderService.cancelOrder(
                customer.getEmail(),
                order.id()
        );

        assertEquals(
                "CANCELLED",
                cancelled.status()
        );

        // Second cancellation must fail.

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(
                        customer.getEmail(),
                        order.id()
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertPaymentStatus(
                order.id(),
                PaymentStatus.REFUNDED
        );

        assertStock(10);

        // Exactly one STOCK_OUT and one STOCK_IN.

        assertEquals(
                2,
                inventoryMovementRepository.count()
        );

        // Toss refund called only once.

        verify(
                tossPaymentClient,
                times(1)
        ).cancelPayment(
                eq(paymentKey),
                anyString(),
                anyString()
        );
    }

    // ============================================================
    // TEST 9: PROCESSING ORDER CAN BE CANCELLED
    // ============================================================

    @Test
    void processingOrderCanBeCancelledBeforeShipping() {

        OrderResponse order = checkoutProduct(2);

        Order savedOrder = orderRepository
                .findById(order.id())
                .orElseThrow();

        savedOrder.setStatus(OrderStatus.PROCESSING);

        orderRepository.saveAndFlush(savedOrder);

        OrderResponse cancelled = orderService.cancelOrder(
                customer.getEmail(),
                order.id()
        );

        assertEquals(
                "CANCELLED",
                cancelled.status()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertPaymentStatus(
                order.id(),
                PaymentStatus.PENDING
        );

        assertStock(10);

        assertEquals(
                2,
                inventoryMovementRepository.count()
        );
    }

    // ============================================================
    // TEST 10: SHIPPED ORDER CANNOT BE CANCELLED
    // ============================================================

    @Test
    void shippedOrderCannotBeCancelled() {

        OrderResponse order = checkoutProduct(2);

        Order savedOrder = orderRepository
                .findById(order.id())
                .orElseThrow();

        savedOrder.setStatus(OrderStatus.SHIPPED);

        orderRepository.saveAndFlush(savedOrder);

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(
                        customer.getEmail(),
                        order.id()
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.SHIPPED
        );

        assertPaymentStatus(
                order.id(),
                PaymentStatus.PENDING
        );

        assertStock(8);

        assertEquals(
                1,
                inventoryMovementRepository.count()
        );

        // No refund request should be made.

        verify(
                tossPaymentClient,
                never()
        ).cancelPayment(
                anyString(),
                anyString(),
                anyString()
        );
    }
}