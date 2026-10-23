package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.CreateOrderRequest;
import com.springcommerce.backend.dto.OrderResponse;
import com.springcommerce.backend.service.OrderService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.checkout(
                authentication.getName(),
                request
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public List<OrderResponse> getOrders(
            Authentication authentication
    ) {
        return orderService.getOrders(
                authentication.getName()
        );
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return orderService.getOrder(
                authentication.getName(),
                orderId
        );
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return orderService.cancelOrder(
                authentication.getName(),
                orderId
        );
    }

    @DeleteMapping("/{orderId}/history")
    public ResponseEntity<Void> removeFromHistory(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        orderService.removeFromHistory(
                authentication.getName(),
                orderId
        );

        return ResponseEntity.noContent().build();
    }
}