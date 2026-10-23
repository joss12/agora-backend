package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.CreateShipmentRequest;
import com.springcommerce.backend.dto.ShipmentResponse;
import com.springcommerce.backend.service.ShipmentService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders/{orderId}/shipment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminShipmentController {

    private final ShipmentService shipmentService;

    public AdminShipmentController(
            ShipmentService shipmentService
    ) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> shipOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody
            CreateShipmentRequest request
    ) {
        return ResponseEntity.ok(
                shipmentService.shipOrder(
                        orderId,
                        request
                )
        );
    }

    @GetMapping
    public ResponseEntity<ShipmentResponse> getShipment(
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                shipmentService.getShipmentByOrder(
                        orderId
                )
        );
    }

    @PatchMapping("/delivered")
    public ResponseEntity<ShipmentResponse> markDelivered(
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                shipmentService.markDelivered(orderId)
        );
    }
}