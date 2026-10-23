package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderItemResponse;
import com.springcommerce.backend.dto.OrderResponse;

import com.springcommerce.backend.entity.Cart;
import com.springcommerce.backend.entity.CartItem;
import com.springcommerce.backend.entity.InventoryMovement;
import com.springcommerce.backend.entity.InventoryMovementType;
import com.springcommerce.backend.entity.Order;
import com.springcommerce.backend.entity.OrderItem;
import com.springcommerce.backend.entity.OrderStatus;
import com.springcommerce.backend.entity.Payment;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.exception.EmptyCartException;
import com.springcommerce.backend.exception.InsufficientStockException;
import com.springcommerce.backend.exception.OrderCancellationException;
import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.PaymentNotFoundException;
import com.springcommerce.backend.exception.ProductNotFoundException;

import com.springcommerce.backend.repository.CartItemRepository;
import com.springcommerce.backend.repository.CartRepository;
import com.springcommerce.backend.repository.InventoryMovementRepository;
import com.springcommerce.backend.repository.OrderItemRepository;
import com.springcommerce.backend.repository.OrderRepository;
import com.springcommerce.backend.repository.PaymentRepository;
import com.springcommerce.backend.repository.ProductRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class OrderService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final PaymentRepository paymentRepository;
    private final CouponCheckoutService couponCheckoutService;
    private final OrderPaymentCancellationService paymentCancellationService;

    public OrderService(
            UserRepository userRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryMovementRepository inventoryMovementRepository,
            PaymentRepository paymentRepository,
            CouponCheckoutService couponCheckoutService,
            OrderPaymentCancellationService paymentCancellationService
    ) {
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.paymentRepository = paymentRepository;
        this.couponCheckoutService = couponCheckoutService;
        this.paymentCancellationService = paymentCancellationService;
    }

    // ============================================================
    // CHECKOUT
    // ============================================================

    @Transactional
    @CacheEvict(
            cacheNames = "products",
            allEntries = true
    )
    public OrderResponse checkout(
            String email,
            CreateOrderRequest request
    ) {
        User user = findUser(email);

        Cart cart = cartRepository
                .findByUserIdForUpdate(user.getId())
                .orElseThrow(EmptyCartException::new);

        List<CartItem> cartItems = new ArrayList<>(
                cartItemRepository
                        .findByCartIdOrderByCreatedAtAsc(
                                cart.getId()
                        )
        );

        if (cartItems.isEmpty()) {
            throw new EmptyCartException();
        }

        cartItems.sort(
                Comparator.comparing(
                        item -> item.getProduct().getId()
                )
        );

        // CREATE ORDER

        Order order = new Order();

        order.setUser(user);
        order.setStatus(OrderStatus.PLACED);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setHiddenByCustomer(false);

        order.setRecipientName(
                request.recipientName().trim()
        );

        order.setPhoneNumber(
                request.phoneNumber().trim()
        );

        order.setPostalCode(
                request.postalCode().trim()
        );

        order.setAddress(
                request.address().trim()
        );

        order.setAddressDetail(
                request.addressDetail() == null
                        ? null
                        : request.addressDetail().trim()
        );

        order.setDeliveryInstructions(
                request.deliveryInstructions() == null
                        ? null
                        : request.deliveryInstructions().trim()
        );

        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();

        BigDecimal orderTotal = BigDecimal.ZERO;

        // RESERVE INVENTORY

        for (CartItem cartItem : cartItems) {

            Long productId = cartItem
                    .getProduct()
                    .getId();

            Product product = productRepository
                    .findByIdForUpdate(productId)
                    .orElseThrow(
                            () -> new ProductNotFoundException(
                                    productId
                            )
                    );

            if (!product.isActive()) {
                throw new ProductNotFoundException(
                        productId
                );
            }

            int quantity = cartItem.getQuantity();

            if (quantity <= 0) {
                throw new IllegalStateException(
                        "Cart item quantity must be greater than zero"
                );
            }

            int stockBefore = product.getStock();

            if (stockBefore < quantity) {
                throw new InsufficientStockException(
                        product.getId(),
                        stockBefore,
                        quantity
                );
            }

            int stockAfter = stockBefore - quantity;

            BigDecimal subtotal = product
                    .getPrice()
                    .multiply(
                            BigDecimal.valueOf(quantity)
                    );

            // CREATE ORDER ITEM SNAPSHOT

            OrderItem orderItem = new OrderItem();

            orderItem.setOrder(savedOrder);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setSku(product.getSku());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setQuantity(quantity);
            orderItem.setSubtotal(subtotal);

            orderItems.add(orderItem);

            orderTotal = orderTotal.add(subtotal);

            // DEDUCT INVENTORY

            product.setStock(stockAfter);

            productRepository.save(product);

            // RECORD INVENTORY MOVEMENT

            InventoryMovement movement =
                    new InventoryMovement();

            movement.setProduct(product);
            movement.setType(
                    InventoryMovementType.STOCK_OUT
            );
            movement.setQuantity(quantity);
            movement.setStockBefore(stockBefore);
            movement.setStockAfter(stockAfter);

            inventoryMovementRepository.save(movement);
        }

        // SAVE ORDER ITEMS

        orderItemRepository.saveAll(orderItems);

        // APPLY COUPON DISCOUNT

        BigDecimal discount = couponCheckoutService.apply(
                request.couponCode(),
                orderTotal,
                user,
                savedOrder
        );

        // CALCULATE FINAL TOTAL

        BigDecimal finalTotal = orderTotal.subtract(discount);

        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Order total cannot be negative"
            );
        }

        if (finalTotal.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException(
                    "Coupon cannot reduce the order total to zero"
            );
        }

        // SAVE FINAL ORDER TOTAL

        savedOrder.setTotalAmount(finalTotal);

        orderRepository.save(savedOrder);

        // CREATE PENDING PAYMENT

        Payment payment = new Payment();

        payment.setOrder(savedOrder);
        payment.setAmount(finalTotal);
        payment.setStatus(PaymentStatus.PENDING);

        paymentRepository.save(payment);

        // EMPTY CUSTOMER CART

        cartItemRepository.deleteByCartId(
                cart.getId()
        );

        return mapToResponse(
                savedOrder,
                orderItems
        );
    }

    // ============================================================
    // CUSTOMER ORDER HISTORY
    // ============================================================

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(
            String email
    ) {
        User user = findUser(email);

        return orderRepository
                .findByUserIdOrderByCreatedAtDesc(
                        user.getId()
                )
                .stream()
                .filter(
                        order -> !order.isHiddenByCustomer()
                )
                .map(
                        order -> mapToResponse(
                                order,
                                orderItemRepository
                                        .findByOrderIdOrderByIdAsc(
                                                order.getId()
                                        )
                        )
                )
                .toList();
    }

    // ============================================================
    // CUSTOMER ORDER DETAILS
    // ============================================================

    @Transactional(readOnly = true)
    public OrderResponse getOrder(
            String email,
            Long orderId
    ) {
        User user = findUser(email);

        Order order = orderRepository
                .findByIdAndUserId(
                        orderId,
                        user.getId()
                )
                .orElseThrow(
                        () -> new OrderNotFoundException(
                                orderId
                        )
                );

        if (order.isHiddenByCustomer()) {
            throw new OrderNotFoundException(orderId);
        }

        List<OrderItem> orderItems =
                orderItemRepository
                        .findByOrderIdOrderByIdAsc(
                                order.getId()
                        );

        return mapToResponse(
                order,
                orderItems
        );
    }

    // ============================================================
    // CANCEL ORDER
    // ============================================================

    @Transactional
    @CacheEvict(
            cacheNames = "products",
            allEntries = true
    )
    public OrderResponse cancelOrder(
            String email,
            Long orderId
    ) {
        User user = findUser(email);

        // Lock the order to serialize cancellation
        // with other order/payment operations.

        Order order = orderRepository
                .findByIdAndUserIdForUpdate(
                        orderId,
                        user.getId()
                )
                .orElseThrow(
                        () -> new OrderNotFoundException(
                                orderId
                        )
                );

        if (order.isHiddenByCustomer()) {
            throw new OrderNotFoundException(orderId);
        }

        // CUSTOMER CAN CANCEL BEFORE SHIPMENT

        if (order.getStatus() != OrderStatus.PLACED
                && order.getStatus() != OrderStatus.PROCESSING) {
            throw new OrderCancellationException(
                    orderId
            );
        }

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(
                                orderId
                        )
                );

        // ========================================================
        // VERIFY PAYMENT / REQUEST REFUND
        // ========================================================
        //
        // This replaces the previous blanket Toss-ID guard.
        //
        // - No Toss payment: resolve local payment state.
        // - Toss DONE: request and verify full refund.
        // - Toss CANCELED: reconcile existing refund.
        // - Toss EXPIRED / ABORTED: verify failed attempt.
        // - Toss READY / IN_PROGRESS: block cancellation.
        //
        // No stock is restored until this method succeeds.

        PaymentStatus resolvedPaymentStatus =
                paymentCancellationService.resolveCancellation(
                        order,
                        payment
                );

        // ========================================================
        // LOAD ORDER ITEMS
        // ========================================================

        List<OrderItem> orderItems =
                new ArrayList<>(
                        orderItemRepository
                                .findByOrderIdOrderByIdAsc(
                                        orderId
                                )
                );

        orderItems.sort(
                Comparator.comparing(
                        item -> item.getProduct().getId()
                )
        );

        // ========================================================
        // RESTORE INVENTORY
        // ========================================================

        for (OrderItem item : orderItems) {

            Long productId = item
                    .getProduct()
                    .getId();

            Product product = productRepository
                    .findByIdForUpdate(productId)
                    .orElseThrow(
                            () -> new ProductNotFoundException(
                                    productId
                            )
                    );

            int quantity = item.getQuantity();

            int stockBefore = product.getStock();

            int stockAfter = Math.addExact(
                    stockBefore,
                    quantity
            );

            product.setStock(stockAfter);

            productRepository.save(product);

            InventoryMovement movement =
                    new InventoryMovement();

            movement.setProduct(product);
            movement.setType(
                    InventoryMovementType.STOCK_IN
            );
            movement.setQuantity(quantity);
            movement.setStockBefore(stockBefore);
            movement.setStockAfter(stockAfter);

            inventoryMovementRepository.save(movement);
        }

        // ========================================================
        // UPDATE PAYMENT STATUS
        // ========================================================

        if (payment.getStatus() != resolvedPaymentStatus) {
            payment.setStatus(resolvedPaymentStatus);

            paymentRepository.save(payment);
        }

        // ========================================================
        // RELEASE COUPON REDEMPTION
        // ========================================================

        couponCheckoutService.release(orderId);

        // ========================================================
        // UPDATE ORDER STATUS
        // ========================================================

        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        return mapToResponse(
                order,
                orderItems
        );
    }

    // ============================================================
    // REMOVE FROM CUSTOMER HISTORY
    // ============================================================

    @Transactional
    public void removeFromHistory(
            String email,
            Long orderId
    ) {
        User user = findUser(email);

        Order order = orderRepository
                .findByIdAndUserIdForUpdate(
                        orderId,
                        user.getId()
                )
                .orElseThrow(
                        () -> new OrderNotFoundException(
                                orderId
                        )
                );

        if (order.isHiddenByCustomer()) {
            return;
        }

        // Active orders must remain visible.
        // Only cancelled orders can be hidden.

        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only cancelled orders can be removed "
                            + "from your order history."
            );
        }

        order.setHiddenByCustomer(true);

        orderRepository.save(order);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private User findUser(
            String email
    ) {
        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        return userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Authenticated user not found"
                        )
                );
    }

    private OrderResponse mapToResponse(
            Order order,
            List<OrderItem> orderItems
    ) {
        List<OrderItemResponse> items =
                orderItems
                        .stream()
                        .map(
                                item -> new OrderItemResponse(
                                        item.getId(),
                                        item.getProduct().getId(),
                                        item.getProductName(),
                                        item.getSku(),
                                        item.getUnitPrice(),
                                        item.getQuantity(),
                                        item.getSubtotal()
                                )
                        )
                        .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                items,
                order.getRecipientName(),
                order.getPhoneNumber(),
                order.getPostalCode(),
                order.getAddress(),
                order.getAddressDetail(),
                order.getDeliveryInstructions(),
                order.getCreatedAt()
        );
    }
}