package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.ShipmentResponse;
import com.springcommerce.backend.service.ShipmentService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/shipment")
public class CustomerShipmentController {

    private final ShipmentService shipmentService;

    public CustomerShipmentController(
            ShipmentService shipmentService
    ) {
        this.shipmentService = shipmentService;
    }

    @GetMapping
    public ResponseEntity<ShipmentResponse> getShipment(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                shipmentService.getCustomerShipment(
                        authentication.getName(),
                        orderId
                )
        );
    }

    @PatchMapping("/confirm-delivery")
    public ResponseEntity<ShipmentResponse> confirmDelivery(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                shipmentService.confirmCustomerDelivery(
                        authentication.getName(),
                        orderId
                )
        );
    }
}