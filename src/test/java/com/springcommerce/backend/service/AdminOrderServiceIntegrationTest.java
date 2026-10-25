package com.springcommerce.backend.service;

import com.springcommerce.backend.controller.DemoPaymentController;
import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.AdminOrderResponse;
import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderResponse;

import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.exception.OrderStatusConflictException;
import com.springcommerce.backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AdminOrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql =
            new MySQLContainer("mysql:8.4");

    @Autowired
    private AdminOrderService adminOrderService;

    @Autowired
    private OrderService orderService;

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

    @Autowired
    private ShipmentRepository shipmentRepository;

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();

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
        customer.setEmail("admin-order-test@example.com");
        customer.setPassword("test-hash");
        customer.setRole(UserRole.CUSTOMER);
        customer.setEnabled(true);

        customer = userRepository.save(customer);

        product = new Product();
        product.setName("Test Monitor");
        product.setSku("ADMIN-MONITOR-001");
        product.setPrice(new BigDecimal("150000.00"));
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

    private DemoPaymentController.DemoPaymentResponse simulate(
            Long orderId,
            DemoPaymentController.DemoOutcome outcome
    ) {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        customer.getEmail(),
                        null,
                        "ROLE_CUSTOMER"
                );

        authentication.setAuthenticated(true);

        return demoPaymentController.simulate(
                authentication,
                orderId,
                new DemoPaymentController.DemoPaymentRequest(
                        outcome
                )
        );
    }

    private Payment getPayment(Long orderId) {
        return paymentRepository
                .findByOrderId(orderId)
                .orElseThrow();
    }

    private void assertOrderStatus(
            Long orderId,
            OrderStatus expectedStatus
    ) {
        assertEquals(
                expectedStatus,
                orderRepository.findById(orderId)
                        .orElseThrow()
                        .getStatus()
        );
    }

    private void assertProductStock(int expectedStock) {
        assertEquals(
                expectedStock,
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );
    }

    @Test
    void adminCanListOrders() {
        OrderResponse order = placeOrder();

        Page<AdminOrderResponse> page =
                adminOrderService.getOrders(
                        null,
                        PageRequest.of(0, 10)
                );

        assertEquals(1, page.getTotalElements());

        AdminOrderResponse result =
                page.getContent().get(0);

        assertEquals(order.id(), result.id());
        assertEquals(customer.getId(), result.customerId());
        assertEquals(customer.getEmail(), result.customerEmail());
        assertEquals("PLACED", result.status());
    }

    @Test
    void adminCanFilterOrdersByStatus() {
        placeOrder();

        Page<AdminOrderResponse> placed =
                adminOrderService.getOrders(
                        OrderStatus.PLACED,
                        PageRequest.of(0, 10)
                );

        Page<AdminOrderResponse> shipped =
                adminOrderService.getOrders(
                        OrderStatus.SHIPPED,
                        PageRequest.of(0, 10)
                );

        assertEquals(1, placed.getTotalElements());
        assertEquals(0, shipped.getTotalElements());
    }

    @Test
    void adminCanViewOrderDetails() {
        OrderResponse order = placeOrder();

        AdminOrderResponse response =
                adminOrderService.getOrder(order.id());

        assertEquals(order.id(), response.id());

        assertEquals(
                customer.getEmail(),
                response.customerEmail()
        );

        assertEquals(1, response.items().size());
        assertEquals("Kim Minjun", response.recipientName());
        assertEquals("06236", response.postalCode());
    }

    @Test
    void unpaidOrderCannotBeProcessed() {
        OrderResponse order = placeOrder();

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.PROCESSING
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PLACED
        );

        assertProductStock(8);
    }

    @Test
    void successfulDemoDoesNotAuthorizeProcessing() {
        OrderResponse order = placeOrder();

        var demo = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        assertEquals("PAID", demo.status());
        assertTrue(demo.demo());
        assertFalse(demo.persisted());

        assertEquals(
                PaymentStatus.PENDING,
                getPayment(order.id()).getStatus()
        );

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.PROCESSING
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PLACED
        );

        assertProductStock(8);
        assertEquals(0, shipmentRepository.count());
    }

    @Test
    void failedDemoDoesNotAuthorizeProcessing() {
        OrderResponse order = placeOrder();

        var demo = simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        assertEquals("FAILED", demo.status());

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.PROCESSING
                )
        );

        assertEquals(
                PaymentStatus.PENDING,
                getPayment(order.id()).getStatus()
        );
    }

    @Test
    void demoDoesNotCreateRealPaymentAttempts() {
        OrderResponse order = placeOrder();

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.FAILURE
        );

        assertEquals(0, paymentAttemptRepository.count());

        Payment payment = getPayment(order.id());

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getTransactionReference());
    }

    @Test
    void adminCannotSkipFulfillmentStages() {
        OrderResponse order = placeOrder();

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.SHIPPED
                )
        );

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.DELIVERED
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PLACED
        );

        assertProductStock(8);
    }

    @Test
    void adminCanCancelUnpaidOrder() {
        OrderResponse order = placeOrder();

        assertProductStock(8);

        AdminOrderResponse cancelled =
                adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                );

        assertEquals("CANCELLED", cancelled.status());

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertProductStock(10);

        assertEquals(
                PaymentStatus.PENDING,
                getPayment(order.id()).getStatus()
        );
    }

    @Test
    void adminCanCancelOrderAfterSuccessfulDemo() {
        OrderResponse order = placeOrder();

        simulate(
                order.id(),
                DemoPaymentController.DemoOutcome.SUCCESS
        );

        AdminOrderResponse cancelled =
                adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                );

        assertEquals("CANCELLED", cancelled.status());

        assertProductStock(10);

        assertEquals(
                PaymentStatus.PENDING,
                getPayment(order.id()).getStatus()
        );

        assertEquals(0, paymentAttemptRepository.count());
    }

    @Test
    void adminCannotProcessCancelledOrder() {
        OrderResponse order = placeOrder();

        adminOrderService.updateStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.PROCESSING
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertProductStock(10);
    }

    @Test
    void adminCannotCancelSameOrderTwice() {
        OrderResponse order = placeOrder();

        adminOrderService.updateStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertProductStock(10);

        long movementsAfterFirstCancellation =
                inventoryMovementRepository.count();

        assertEquals(
                2,
                movementsAfterFirstCancellation
        );

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertProductStock(10);

        assertEquals(
                movementsAfterFirstCancellation,
                inventoryMovementRepository.count()
        );

        assertEquals(
                PaymentStatus.PENDING,
                getPayment(order.id()).getStatus()
        );
    }

    @Test
    void adminCannotCancelTossPreparedOrder() {
        OrderResponse order = placeOrder();

        Payment payment = getPayment(order.id());

        payment.setTossOrderId(
                "TOSS-TEST-" + order.id()
        );

        paymentRepository.saveAndFlush(payment);

        assertThrows(
                ResponseStatusException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PLACED
        );

        assertProductStock(8);

        assertEquals(
                PaymentStatus.PENDING,
                getPayment(order.id()).getStatus()
        );

        assertEquals(
                1,
                inventoryMovementRepository.count()
        );
    }
}