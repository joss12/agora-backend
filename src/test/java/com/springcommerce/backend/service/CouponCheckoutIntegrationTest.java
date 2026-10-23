package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderResponse;

import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CouponCheckoutIntegrationTest {

    // =========================================================
    // TESTCONTAINERS
    // =========================================================

    @Container
    @ServiceConnection
    static MySQLContainer mysql =
            new MySQLContainer("mysql:8.4");

    @Autowired
    private DataSource dataSource;

    // =========================================================
    // SERVICES
    // =========================================================

    @Autowired
    private OrderService orderService;

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

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponRedemptionRepository redemptionRepository;

    // =========================================================
    // TEST DATA
    // =========================================================

    private User customer;
    private Product product;
    private Coupon coupon;

    // =========================================================
    // DATABASE SAFETY
    // =========================================================

    private void verifyTestDatabase() {

        assertTrue(
                mysql.isRunning(),
                "The MySQL Testcontainer must be running."
        );

        try (Connection connection = dataSource.getConnection()) {

            String actualUrl = connection
                    .getMetaData()
                    .getURL();

            String expectedUrl = mysql
                    .getJdbcUrl()
                    .split("\\?")[0];

            assertNotNull(actualUrl);

            assertTrue(
                    actualUrl.startsWith(expectedUrl),
                    "UNSAFE TEST DATABASE CONNECTION!\n"
                            + "Expected Testcontainers database: "
                            + expectedUrl
                            + "\nActual database: "
                            + actualUrl
                            + "\nRefusing to delete existing data."
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                    "Cannot verify the test database connection.",
                    exception
            );
        }
    }

    // =========================================================
    // SETUP
    // =========================================================

    @BeforeEach
    void setUp() {

        // CRITICAL:
        // Verify the active database before deleting test data.
        // Never clean the development or production Agora database.

        verifyTestDatabase();

        // =====================================================
        // CLEANUP
        // =====================================================

        redemptionRepository.deleteAll();

        paymentAttemptRepository.deleteAll();
        paymentRepository.deleteAll();

        inventoryMovementRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        cartItemRepository.deleteAll();
        cartRepository.deleteAll();

        couponRepository.deleteAll();

        productRepository.deleteAll();
        userRepository.deleteAll();

        // =====================================================
        // CREATE TEST CUSTOMER
        // =====================================================

        customer = new User();

        customer.setFirstName("Test");
        customer.setLastName("Customer");
        customer.setEmail("coupon-test@example.com");
        customer.setPassword("test-hash");
        customer.setRole(UserRole.CUSTOMER);
        customer.setEnabled(true);

        customer = userRepository.save(customer);

        // =====================================================
        // CREATE TEST PRODUCT
        // =====================================================

        product = new Product();

        product.setName("Coupon Test Keyboard");
        product.setSku("COUPON-KB-001");

        product.setPrice(
                new BigDecimal("50000.00")
        );

        product.setStock(10);
        product.setActive(true);

        product = productRepository.save(product);

        // =====================================================
        // CREATE WELCOME10 COUPON
        // =====================================================

        coupon = new Coupon();

        coupon.setCode("WELCOME10");

        coupon.setDiscountType(
                DiscountType.PERCENTAGE
        );

        coupon.setDiscountValue(
                new BigDecimal("10.00")
        );

        coupon.setMinimumOrderAmount(
                new BigDecimal("50000.00")
        );

        coupon.setMaximumDiscountAmount(
                new BigDecimal("20000.00")
        );

        coupon.setUsageLimit(100);
        coupon.setUsageLimitPerUser(1);
        coupon.setUsageCount(0);

        coupon.setStartsAt(
                LocalDateTime.now().minusDays(1)
        );

        coupon.setExpiresAt(
                LocalDateTime.now().plusDays(30)
        );

        coupon.setActive(true);
        coupon.setDeleted(false);

        coupon = couponRepository.save(coupon);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private CreateOrderRequest checkoutRequest(
            String couponCode
    ) {

        return new CreateOrderRequest(
                "Kim Minjun",
                "010-1234-5678",
                "06236",
                "123 Teheran-ro, Gangnam-gu, Seoul",
                "Apartment 502",
                "Leave at the door",
                couponCode
        );
    }

    private void addProductsToCart(int quantity) {

        cartService.addItem(
                customer.getEmail(),
                new AddCartItemRequest(
                        product.getId(),
                        quantity
                )
        );
    }

    private void assertAmount(
            String expected,
            BigDecimal actual
    ) {

        assertNotNull(actual);

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }

    private Coupon reloadCoupon() {

        return couponRepository
                .findById(coupon.getId())
                .orElseThrow();
    }

    // =========================================================
    // TEST 1: VALID COUPON
    // =========================================================

    @Test
    void validCouponAppliesTenPercentDiscount() {

        // 2 × ₩50,000 = ₩100,000
        // WELCOME10 = ₩10,000 discount
        // Final total = ₩90,000

        addProductsToCart(2);

        OrderResponse response = orderService.checkout(
                customer.getEmail(),
                checkoutRequest("WELCOME10")
        );

        assertNotNull(response.id());

        assertEquals(
                "PLACED",
                response.status()
        );

        assertAmount(
                "90000.00",
                response.totalAmount()
        );

        // Verify persisted order.

        Order savedOrder = orderRepository
                .findById(response.id())
                .orElseThrow();

        assertAmount(
                "90000.00",
                savedOrder.getTotalAmount()
        );

        // Verify redemption.

        CouponRedemption redemption = redemptionRepository
                .findByOrderId(response.id())
                .orElseThrow();

        assertAmount(
                "10000.00",
                redemption.getDiscountAmount()
        );

        assertEquals(
                coupon.getId(),
                redemption.getCoupon().getId()
        );

        assertEquals(
                customer.getId(),
                redemption.getUser().getId()
        );

        // Verify usage count.

        assertEquals(
                1,
                reloadCoupon().getUsageCount()
        );

        // Verify payment uses discounted amount.

        Payment payment = paymentRepository
                .findByOrderId(response.id())
                .orElseThrow();

        assertEquals(
                PaymentStatus.PENDING,
                payment.getStatus()
        );

        assertAmount(
                "90000.00",
                payment.getAmount()
        );
    }

    // =========================================================
    // TEST 2: INVALID COUPON
    // =========================================================

    @Test
    void invalidCouponRollsBackCheckout() {

        addProductsToCart(2);

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.checkout(
                        customer.getEmail(),
                        checkoutRequest("INVALID123")
                )
        );

        // No order, payment or redemption.

        assertEquals(0, orderRepository.count());
        assertEquals(0, orderItemRepository.count());
        assertEquals(0, paymentRepository.count());
        assertEquals(0, redemptionRepository.count());

        // Stock must remain unchanged.

        assertEquals(
                10,
                productRepository
                        .findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );

        // Cart must remain intact.

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

    // =========================================================
    // TEST 3: EXPIRED COUPON
    // =========================================================

    @Test
    void expiredCouponIsRejected() {

        coupon.setStartsAt(
                LocalDateTime.now().minusDays(10)
        );

        coupon.setExpiresAt(
                LocalDateTime.now().minusDays(1)
        );

        couponRepository.saveAndFlush(coupon);

        addProductsToCart(2);

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.checkout(
                        customer.getEmail(),
                        checkoutRequest("WELCOME10")
                )
        );

        assertEquals(0, orderRepository.count());
        assertEquals(0, redemptionRepository.count());

        assertEquals(
                0,
                reloadCoupon().getUsageCount()
        );
    }

    // =========================================================
    // TEST 4: PER-USER USAGE LIMIT
    // =========================================================

    @Test
    void customerCannotRedeemSameCouponTwice() {

        addProductsToCart(2);

        OrderResponse firstOrder = orderService.checkout(
                customer.getEmail(),
                checkoutRequest("WELCOME10")
        );

        assertNotNull(firstOrder.id());

        // First checkout clears the cart.

        addProductsToCart(1);

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.checkout(
                        customer.getEmail(),
                        checkoutRequest("WELCOME10")
                )
        );

        // Only the first checkout must persist.

        assertEquals(1, orderRepository.count());
        assertEquals(1, redemptionRepository.count());

        assertEquals(
                1,
                reloadCoupon().getUsageCount()
        );
    }

    // =========================================================
    // TEST 5: GLOBAL USAGE LIMIT
    // =========================================================

    @Test
    void globalCouponUsageLimitIsEnforced() {

        // IMPORTANT FIX:
        //
        // Configure BOTH limits before the first checkout.
        //
        // Do not save the original coupon object after the
        // first checkout because its usageCount may be stale.

        coupon.setUsageLimit(1);
        coupon.setUsageLimitPerUser(5);

        coupon = couponRepository.saveAndFlush(coupon);

        // =====================================================
        // FIRST CHECKOUT
        // =====================================================

        // 2 × ₩50,000 = ₩100,000

        addProductsToCart(2);

        OrderResponse firstOrder = orderService.checkout(
                customer.getEmail(),
                checkoutRequest("WELCOME10")
        );

        assertNotNull(firstOrder.id());

        assertAmount(
                "90000.00",
                firstOrder.totalAmount()
        );

        // The global redemption limit is now exhausted.

        assertEquals(
                1,
                reloadCoupon().getUsageCount()
        );

        assertEquals(
                1,
                redemptionRepository.count()
        );

        // =====================================================
        // SECOND CHECKOUT
        // =====================================================

        // Per-user limit allows 5 redemptions,
        // but the global limit allows only 1.

        addProductsToCart(1);

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.checkout(
                        customer.getEmail(),
                        checkoutRequest("WELCOME10")
                )
        );

        // =====================================================
        // VERIFY ROLLBACK
        // =====================================================

        assertEquals(
                1,
                orderRepository.count()
        );

        assertEquals(
                1,
                redemptionRepository.count()
        );

        assertEquals(
                1,
                reloadCoupon().getUsageCount()
        );

        // Only the first order should have a payment.

        assertEquals(
                1,
                paymentRepository.count()
        );

        // First checkout consumed 2 units.
        // Failed second checkout must not consume more.

        assertEquals(
                8,
                productRepository
                        .findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );
    }

    // =========================================================
    // TEST 6: CANCEL RELEASES COUPON
    // =========================================================

    @Test
    void cancellingOrderReleasesCouponRedemption() {

        addProductsToCart(2);

        OrderResponse placedOrder = orderService.checkout(
                customer.getEmail(),
                checkoutRequest("WELCOME10")
        );

        assertEquals(
                1,
                reloadCoupon().getUsageCount()
        );

        assertTrue(
                redemptionRepository
                        .findByOrderId(placedOrder.id())
                        .isPresent()
        );

        // Cancel the unpaid order.

        OrderResponse cancelledOrder = orderService.cancelOrder(
                customer.getEmail(),
                placedOrder.id()
        );

        assertEquals(
                "CANCELLED",
                cancelledOrder.status()
        );

        // Redemption must be released.

        assertTrue(
                redemptionRepository
                        .findByOrderId(placedOrder.id())
                        .isEmpty()
        );

        assertEquals(
                0,
                reloadCoupon().getUsageCount()
        );

        // Inventory must be restored.

        assertEquals(
                10,
                productRepository
                        .findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );

        // Customer should be able to use the coupon again.

        addProductsToCart(1);

        OrderResponse secondOrder = orderService.checkout(
                customer.getEmail(),
                checkoutRequest("WELCOME10")
        );

        assertNotNull(secondOrder.id());

        assertAmount(
                "45000.00",
                secondOrder.totalAmount()
        );

        assertEquals(
                1,
                reloadCoupon().getUsageCount()
        );
    }

    // =========================================================
    // TEST 7: MAXIMUM DISCOUNT CAP
    // =========================================================

    @Test
    void percentageDiscountRespectsMaximumCap() {

        // 5 × ₩50,000 = ₩250,000
        // 10% discount = ₩25,000
        // Maximum allowed discount = ₩20,000
        // Final total = ₩230,000

        addProductsToCart(5);

        OrderResponse response = orderService.checkout(
                customer.getEmail(),
                checkoutRequest("WELCOME10")
        );

        assertAmount(
                "230000.00",
                response.totalAmount()
        );

        CouponRedemption redemption = redemptionRepository
                .findByOrderId(response.id())
                .orElseThrow();

        assertAmount(
                "20000.00",
                redemption.getDiscountAmount()
        );
    }

    // =========================================================
    // TEST 8: MINIMUM ORDER AMOUNT
    // =========================================================

    @Test
    void couponRequiresMinimumOrderAmount() {

        coupon.setMinimumOrderAmount(
                new BigDecimal("100000.00")
        );

        couponRepository.saveAndFlush(coupon);

        // Cart subtotal is only ₩50,000.

        addProductsToCart(1);

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.checkout(
                        customer.getEmail(),
                        checkoutRequest("WELCOME10")
                )
        );

        assertEquals(
                0,
                orderRepository.count()
        );

        assertEquals(
                0,
                redemptionRepository.count()
        );

        assertEquals(
                0,
                reloadCoupon().getUsageCount()
        );

        // Failed checkout must preserve inventory.

        assertEquals(
                10,
                productRepository
                        .findById(product.getId())
                        .orElseThrow()
                        .getStock()
        );
    }

    // =========================================================
    // TEST 9: NO COUPON
    // =========================================================

    @Test
    void checkoutWithoutCouponUsesFullPrice() {

        addProductsToCart(2);

        OrderResponse response = orderService.checkout(
                customer.getEmail(),
                checkoutRequest(null)
        );

        // No discount applied.

        assertAmount(
                "100000.00",
                response.totalAmount()
        );

        // No redemption created.

        assertEquals(
                0,
                redemptionRepository.count()
        );

        assertEquals(
                0,
                reloadCoupon().getUsageCount()
        );

        // Payment amount equals the original subtotal.

        Payment payment = paymentRepository
                .findByOrderId(response.id())
                .orElseThrow();

        assertAmount(
                "100000.00",
                payment.getAmount()
        );
    }
}