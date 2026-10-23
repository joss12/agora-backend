package com.springcommerce.backend.service;
import org.springframework.cache.annotation.CacheEvict;

import com.springcommerce.backend.dto.AdminOrderResponse;
import com.springcommerce.backend.dto.OrderItemResponse;
import com.springcommerce.backend.entity.InventoryMovement;
import com.springcommerce.backend.entity.InventoryMovementType;
import com.springcommerce.backend.entity.Order;
import com.springcommerce.backend.entity.OrderItem;
import com.springcommerce.backend.entity.OrderStatus;
import com.springcommerce.backend.entity.Payment;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.exception.OrderNotFoundException;
import com.springcommerce.backend.exception.OrderStatusConflictException;
import com.springcommerce.backend.exception.PaymentNotFoundException;
import com.springcommerce.backend.repository.InventoryMovementRepository;
import com.springcommerce.backend.repository.OrderItemRepository;
import com.springcommerce.backend.repository.OrderRepository;
import com.springcommerce.backend.repository.PaymentRepository;
import com.springcommerce.backend.repository.ProductRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    public AdminOrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductRepository productRepository,
            InventoryMovementRepository inventoryMovementRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getOrders(
            OrderStatus status,
            Pageable pageable
    ) {
        Page<Order> orders = status == null
                ? orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(
                status,
                pageable
        );

        return orders.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse getOrder(
            Long orderId
    ) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> getOrdersByCustomer(
            Long customerId
    ) {
        return orderRepository
                .findByUserIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(
            cacheNames = "products",
            allEntries = true
    )
    public AdminOrderResponse updateStatus(
            Long orderId,
            OrderStatus requestedStatus
    ) {
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        if (requestedStatus == null) {
            throw new OrderStatusConflictException(
                    "Order status is required"
            );
        }

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == requestedStatus) {
            throw new OrderStatusConflictException(
                    "Order already has status "
                            + requestedStatus
            );
        }

        if (requestedStatus == OrderStatus.CANCELLED) {
            return cancelOrder(order);
        }

        if (requestedStatus == OrderStatus.SHIPPED) {
            throw new OrderStatusConflictException(
                    "Use the shipment endpoint to ship an order"
            );
        }

        if (requestedStatus == OrderStatus.DELIVERED) {
            throw new OrderStatusConflictException(
                    "Use the shipment endpoint to mark an order as delivered"
            );
        }

        boolean validTransition =
                currentStatus == OrderStatus.PLACED
                        && requestedStatus == OrderStatus.PROCESSING;

        if (!validTransition) {
            throw new OrderStatusConflictException(
                    "Unsupported order status transition: "
                            + currentStatus
                            + " -> "
                            + requestedStatus
            );
        }

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new PaymentNotFoundException(orderId)
                );

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new OrderStatusConflictException(
                    "Order must be paid before fulfillment"
            );
        }

        order.setStatus(requestedStatus);

        Order savedOrder =
                orderRepository.save(order);

        return mapToResponse(savedOrder);
    }

    private AdminOrderResponse cancelOrder(
            Order order
    ) {
        OrderStatus currentStatus =
                order.getStatus();

        if (
                currentStatus != OrderStatus.PLACED
                        && currentStatus
                        != OrderStatus.PROCESSING
        ) {
            throw new OrderStatusConflictException(
                    "Order cannot be cancelled from status "
                            + currentStatus
            );
        }

        Payment payment = paymentRepository
                .findByOrderId(order.getId())
                .orElseThrow(
                        () -> new PaymentNotFoundException(
                                order.getId()
                        )
                );

        if (payment.getTossOrderId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Orders associated with Toss Payments cannot be cancelled."
            );
        }

        List<OrderItem> orderItems =
                new ArrayList<>(
                        orderItemRepository
                                .findByOrderIdOrderByIdAsc(
                                        order.getId()
                                )
                );

        orderItems.sort(
                Comparator.comparing(
                        item ->
                                item.getProduct().getId()
                )
        );

        for (OrderItem item : orderItems) {

            Long productId =
                    item.getProduct().getId();

            Product product = productRepository
                    .findByIdForUpdate(productId)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "Product not found while cancelling order: "
                                            + productId
                            )
                    );

            int quantity =
                    item.getQuantity();

            int stockBefore =
                    product.getStock();

            int stockAfter =
                    Math.addExact(
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

            inventoryMovementRepository.save(
                    movement
            );
        }

        if (
                payment.getStatus()
                        == PaymentStatus.PAID
        ) {
            payment.setStatus(
                    PaymentStatus.REFUNDED
            );

            paymentRepository.save(payment);
        }

        order.setStatus(
                OrderStatus.CANCELLED
        );

        Order savedOrder =
                orderRepository.save(order);

        return mapToResponse(savedOrder);
    }

    private AdminOrderResponse mapToResponse(
            Order order
    ) {
        User customer =
                order.getUser();

        List<OrderItemResponse> items =
                orderItemRepository
                        .findByOrderIdOrderByIdAsc(
                                order.getId()
                        )
                        .stream()
                        .map(
                                item ->
                                        new OrderItemResponse(
                                                item.getId(),
                                                item.getProduct()
                                                        .getId(),
                                                item.getProductName(),
                                                item.getSku(),
                                                item.getUnitPrice(),
                                                item.getQuantity(),
                                                item.getSubtotal()
                                        )
                        )
                        .toList();

        String paymentStatus =
                paymentRepository
                        .findByOrderId(
                                order.getId()
                        )
                        .map(
                                payment ->
                                        payment.getStatus()
                                                .name()
                        )
                        .orElse("PENDING");

        return new AdminOrderResponse(
                order.getId(),
                customer.getId(),
                customer.getFirstName()
                        + " "
                        + customer.getLastName(),
                customer.getEmail(),
                order.getStatus().name(),
                order.getTotalAmount(),
                items,
                order.getRecipientName(),
                order.getPhoneNumber(),
                order.getPostalCode(),
                order.getAddress(),
                order.getAddressDetail(),
                order.getDeliveryInstructions(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                paymentStatus
        );
    }
}