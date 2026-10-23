package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.Shipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository
        extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByOrderId(Long orderId);

    Optional<Shipment> findByTrackingNumber(
            String trackingNumber
    );

    boolean existsByOrderId(Long orderId);

    boolean existsByTrackingNumber(
            String trackingNumber
    );
}