package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.CreateShipmentRequest;
import com.springcommerce.backend.dto.ShipmentResponse;

import com.springcommerce.backend.entity.Order;
import com.springcommerce.backend.entity.OrderStatus;
import com.springcommerce.backend.entity.Payment;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.entity.Shipment;
import com.springcommerce.backend.entity.ShipmentStatus;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.repository.OrderRepository;
import com.springcommerce.backend.repository.PaymentRepository;
import com.springcommerce.backend.repository.ShipmentRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository
    ) {
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    // ============================================================
    // ADMIN — CREATE SHIPMENT
    // ============================================================

    @Transactional
    public ShipmentResponse shipOrder(
            Long orderId,
            CreateShipmentRequest request
    ) {
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found: " + orderId
                        )
                );

        // Only processing orders can be shipped.
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only PROCESSING orders can be shipped."
            );
        }

        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Payment not found for order: " + orderId
                        )
                );

        // Shipping requires a successful payment.
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order must be paid before shipping."
            );
        }

        // Prevent multiple shipments for one order.
        if (shipmentRepository.existsByOrderId(orderId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shipment already exists for this order."
            );
        }

        String carrier = request.carrier().trim();
        String trackingNumber = request.trackingNumber().trim();

        // Validate trimmed values.
        if (carrier.isBlank() || trackingNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Carrier and tracking number are required."
            );
        }

        // Tracking numbers must be unique.
        if (shipmentRepository.existsByTrackingNumber(
                trackingNumber
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This tracking number is already assigned to another shipment."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        Shipment shipment = new Shipment();

        shipment.setOrder(order);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.SHIPPED);
        shipment.setShippedAt(now);

        Shipment savedShipment =
                shipmentRepository.save(shipment);

        // Update order only after shipment creation.
        order.setStatus(OrderStatus.SHIPPED);

        orderRepository.save(order);

        return mapToResponse(savedShipment);
    }

    // ============================================================
    // ADMIN — GET SHIPMENT
    // ============================================================

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByOrder(
            Long orderId
    ) {
        Shipment shipment = shipmentRepository
                .findByOrderId(orderId)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Shipment not found for order: " + orderId
                        )
                );

        return mapToResponse(shipment);
    }

    // ============================================================
    // ADMIN — MARK DELIVERED
    // ============================================================

    @Transactional
    public ShipmentResponse markDelivered(
            Long orderId
    ) {
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found: " + orderId
                        )
                );

        return completeDelivery(order);
    }

    // ============================================================
    // CUSTOMER — GET SHIPMENT
    // ============================================================

    @Transactional(readOnly = true)
    public ShipmentResponse getCustomerShipment(
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
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found: " + orderId
                        )
                );

        Shipment shipment = shipmentRepository
                .findByOrderId(order.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Shipment not found for order: " + orderId
                        )
                );

        return mapToResponse(shipment);
    }

    // ============================================================
    // CUSTOMER — CONFIRM DELIVERY
    // ============================================================

    @Transactional
    public ShipmentResponse confirmCustomerDelivery(
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
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found: " + orderId
                        )
                );

        return completeDelivery(order);
    }

    // ============================================================
    // SHARED — COMPLETE DELIVERY
    // ============================================================

    private ShipmentResponse completeDelivery(
            Order order
    ) {
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only SHIPPED orders can be delivered."
            );
        }

        Shipment shipment = shipmentRepository
                .findByOrderId(order.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Shipment not found for order: "
                                        + order.getId()
                        )
                );

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Shipment has already been delivered."
            );
        }

        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(LocalDateTime.now());

        Shipment savedShipment =
                shipmentRepository.save(shipment);

        order.setStatus(OrderStatus.DELIVERED);

        orderRepository.save(order);

        return mapToResponse(savedShipment);
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

    private ShipmentResponse mapToResponse(
            Shipment shipment
    ) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getOrder().getId(),
                shipment.getCarrier(),
                shipment.getTrackingNumber(),
                shipment.getStatus().name(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt(),
                shipment.getCreatedAt(),
                shipment.getUpdatedAt()
        );
    }
}