package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.AdminOrderResponse;
import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.CreateShipmentRequest;
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
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AdminOrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql =
            new MySQLContainer("mysql:8.4");

    // =========================================================
    // SERVICES
    // =========================================================

    @Autowired
    private AdminOrderService adminOrderService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private CartService cartService;

    // =========================================================
    // REPOSITORIES
    // =========================================================

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

    // =========================================================
    // TEST DATA
    // =========================================================

    private User customer;
    private Product product;

    @BeforeEach
    void setUp() {

        // Delete dependent records first to avoid
        // foreign-key constraint violations.

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

        // -----------------------------------------------------
        // CUSTOMER
        // -----------------------------------------------------

        customer = new User();

        customer.setFirstName("Test");
        customer.setLastName("Customer");
        customer.setEmail("admin-order-test@example.com");
        customer.setPassword("test-hash");
        customer.setRole(UserRole.CUSTOMER);
        customer.setEnabled(true);

        customer = userRepository.save(customer);

        // -----------------------------------------------------
        // PRODUCT
        // -----------------------------------------------------

        product = new Product();

        product.setName("Test Monitor");
        product.setSku("ADMIN-MONITOR-001");
        product.setPrice(new BigDecimal("150000.00"));
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

    private OrderResponse placeAndPayOrder() {

        OrderResponse order = placeOrder();

        paymentService.simulate(
                customer.getEmail(),
                order.id(),
                PaymentOutcome.SUCCESS
        );

        return order;
    }

    private void assertOrderStatus(
            Long orderId,
            OrderStatus expectedStatus
    ) {

        OrderStatus actualStatus = orderRepository
                .findById(orderId)
                .orElseThrow()
                .getStatus();

        assertEquals(expectedStatus, actualStatus);
    }

    private void assertProductStock(int expectedStock) {

        int actualStock = productRepository
                .findById(product.getId())
                .orElseThrow()
                .getStock();

        assertEquals(expectedStock, actualStock);
    }

    // =========================================================
    // TEST 1: ADMIN ORDER LIST
    // =========================================================

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

    // =========================================================
    // TEST 2: FILTER BY STATUS
    // =========================================================

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

    // =========================================================
    // TEST 3: ADMIN ORDER DETAILS
    // =========================================================

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

    // =========================================================
    // TEST 4: UNPAID ORDER
    // =========================================================

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

    // =========================================================
    // TEST 5: FULL FULFILLMENT
    // =========================================================

    @Test
    void paidOrderCanProgressThroughFulfillment() {

        // -----------------------------------------------------
        // 1. CUSTOMER PLACES AND PAYS FOR ORDER
        // -----------------------------------------------------

        OrderResponse order = placeAndPayOrder();

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PAID,
                payment.getStatus()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PLACED
        );

        assertProductStock(8);

        // -----------------------------------------------------
        // 2. ADMIN STARTS PROCESSING
        // -----------------------------------------------------

        AdminOrderResponse processing =
                adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.PROCESSING
                );

        assertEquals(
                "PROCESSING",
                processing.status()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PROCESSING
        );

        // -----------------------------------------------------
        // 3. ADMIN CREATES SHIPMENT
        // -----------------------------------------------------

        shipmentService.shipOrder(
                order.id(),
                new CreateShipmentRequest(
                        "CJ Logistics",
                        "AGORA-TRACK-001"
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.SHIPPED
        );

        Shipment shipment = shipmentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                ShipmentStatus.SHIPPED,
                shipment.getStatus()
        );

        assertEquals(
                "CJ Logistics",
                shipment.getCarrier()
        );

        assertEquals(
                "AGORA-TRACK-001",
                shipment.getTrackingNumber()
        );

        assertNotNull(shipment.getShippedAt());

        // -----------------------------------------------------
        // 4. CUSTOMER CONFIRMS DELIVERY
        // -----------------------------------------------------

        shipmentService.confirmCustomerDelivery(
                customer.getEmail(),
                order.id()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.DELIVERED
        );

        Shipment deliveredShipment = shipmentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                ShipmentStatus.DELIVERED,
                deliveredShipment.getStatus()
        );

        assertNotNull(
                deliveredShipment.getDeliveredAt()
        );

        // -----------------------------------------------------
        // 5. INVENTORY MUST NOT BE DEDUCTED AGAIN
        // -----------------------------------------------------

        assertProductStock(8);

        // -----------------------------------------------------
        // 6. PAYMENT MUST REMAIN PAID
        // -----------------------------------------------------

        Payment completedPayment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PAID,
                completedPayment.getStatus()
        );
    }

    // =========================================================
    // TEST 6: INVALID STATUS TRANSITION
    // =========================================================

    @Test
    void adminCannotSkipFulfillmentStages() {

        OrderResponse order = placeAndPayOrder();

        // Admin cannot ship directly through updateStatus.

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.SHIPPED
                )
        );

        // Admin cannot mark an order delivered directly.

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

    // =========================================================
    // TEST 7: ADMIN CANCELS UNPAID ORDER
    // =========================================================

    @Test
    void adminCanCancelUnpaidOrder() {

        OrderResponse order = placeOrder();

        assertProductStock(8);

        AdminOrderResponse cancelled =
                adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                );

        assertEquals(
                "CANCELLED",
                cancelled.status()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        // Inventory must be restored.

        assertProductStock(10);

        // No successful payment occurred.

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );
    }

    // =========================================================
    // TEST 8: ADMIN CANCELS PAID ORDER
    // =========================================================

    @Test
    void adminCanCancelPaidOrder() {

        OrderResponse order = placeAndPayOrder();

        assertProductStock(8);

        AdminOrderResponse cancelled =
                adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                );

        assertEquals(
                "CANCELLED",
                cancelled.status()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        // Restore reserved inventory.

        assertProductStock(10);

        // Simulated payment must be refunded.

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );
    }

    // =========================================================
    // TEST 9: ADMIN CANCELS PROCESSING ORDER
    // =========================================================

    @Test
    void adminCanCancelProcessingOrder() {

        OrderResponse order = placeAndPayOrder();

        adminOrderService.updateStatus(
                order.id(),
                OrderStatus.PROCESSING
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.PROCESSING
        );

        AdminOrderResponse cancelled =
                adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                );

        assertEquals(
                "CANCELLED",
                cancelled.status()
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertProductStock(10);

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );
    }

    // =========================================================
    // TEST 10: CANNOT CANCEL SHIPPED ORDER
    // =========================================================

    @Test
    void adminCannotCancelShippedOrder() {

        OrderResponse order = placeAndPayOrder();

        adminOrderService.updateStatus(
                order.id(),
                OrderStatus.PROCESSING
        );

        shipmentService.shipOrder(
                order.id(),
                new CreateShipmentRequest(
                        "CJ Logistics",
                        "AGORA-TRACK-002"
                )
        );

        assertOrderStatus(
                order.id(),
                OrderStatus.SHIPPED
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
                OrderStatus.SHIPPED
        );

        assertProductStock(8);
    }

    // =========================================================
    // TEST 11: CANNOT PROCESS CANCELLED ORDER
    // =========================================================

    @Test
    void cancelledOrderCannotBeProcessed() {

        OrderResponse order = placeAndPayOrder();

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

    // =========================================================
    // TEST 12: CANNOT PROCESS SAME ORDER TWICE
    // =========================================================

    @Test
    void adminCannotProcessOrderTwice() {

        OrderResponse order = placeAndPayOrder();

        adminOrderService.updateStatus(
                order.id(),
                OrderStatus.PROCESSING
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
                OrderStatus.PROCESSING
        );

        assertProductStock(8);
    }

    // =========================================================
    // TEST 13: FULFILLMENT PRESERVES ORDER TOTAL
    // =========================================================

    @Test
    void fulfillmentPreservesOrderTotal() {

        OrderResponse order = placeAndPayOrder();

        BigDecimal originalTotal = order.totalAmount();

        adminOrderService.updateStatus(
                order.id(),
                OrderStatus.PROCESSING
        );

        shipmentService.shipOrder(
                order.id(),
                new CreateShipmentRequest(
                        "CJ Logistics",
                        "AGORA-TRACK-003"
                )
        );

        shipmentService.markDelivered(order.id());

        AdminOrderResponse completedOrder =
                adminOrderService.getOrder(order.id());

        assertEquals(
                0,
                originalTotal.compareTo(
                        completedOrder.totalAmount()
                )
        );

        assertEquals(
                "DELIVERED",
                completedOrder.status()
        );

        assertProductStock(8);
    }

    // =========================================================
    // TEST 14: DUPLICATE CANCELLATION MUST NOT RESTOCK TWICE
    // =========================================================

    @Test
    void adminCannotCancelSameOrderTwice() {

        OrderResponse order = placeAndPayOrder();

        assertProductStock(8);

        // First cancellation should succeed.
        // Inventory should return to 10.

        adminOrderService.updateStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        assertOrderStatus(
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

        // A second cancellation must be rejected.

        assertThrows(
                OrderStatusConflictException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                )
        );

        // The order must remain cancelled.

        assertOrderStatus(
                order.id(),
                OrderStatus.CANCELLED
        );

        // Inventory must not increase beyond the
        // original stock of 10.

        assertProductStock(10);

        // No additional inventory movement should exist.

        assertEquals(
                movementsAfterFirstCancellation,
                inventoryMovementRepository.count()
        );

        // The simulated payment must remain refunded.

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );

        // The original order must still exist.

        assertEquals(
                1,
                orderRepository.count()
        );
    }
    @Test
    void adminCannotCancelTossPreparedOrder() {

        OrderResponse order = placeOrder();

        Payment payment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        payment.setTossOrderId("TOSS-TEST-" + order.id());
        paymentRepository.saveAndFlush(payment);

        assertThrows(
                ResponseStatusException.class,
                () -> adminOrderService.updateStatus(
                        order.id(),
                        OrderStatus.CANCELLED
                )
        );

        assertOrderStatus(order.id(), OrderStatus.PLACED);
        assertProductStock(8);

        Payment unchangedPayment = paymentRepository
                .findByOrderId(order.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                unchangedPayment.getStatus()
        );

        assertEquals(1, inventoryMovementRepository.count());
    }
}