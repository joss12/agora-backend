package com.springcommerce.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "shipment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_shipment_order",
                        columnNames = "order_id"
                ),
                @UniqueConstraint(
                        name = "uk_shipment_tracking_number",
                        columnNames = "tracking_number"
                )
        }
)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            unique = true
    )
    private Order order;

    @Column(nullable = false, length = 100)
    private String carrier;

    @Column(
            name = "tracking_number",
            nullable = false,
            length = 150,
            unique = true
    )
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = ShipmentStatus.PREPARING;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(
            String trackingNumber
    ) {
        this.trackingNumber = trackingNumber;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(
            ShipmentStatus status
    ) {
        this.status = status;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(
            LocalDateTime shippedAt
    ) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(
            LocalDateTime deliveredAt
    ) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}