
package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.AdminOrderResponse;
import com.springcommerce.backend.dto.UpdateOrderStatusRequest;

import com.springcommerce.backend.entity.OrderStatus;
import com.springcommerce.backend.service.AdminOrderService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(
            AdminOrderService adminOrderService
    ) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public Page<AdminOrderResponse> getOrders(
            @RequestParam(required = false)
            OrderStatus status,
            Pageable pageable
    ) {

        return adminOrderService.getOrders(
                status,
                pageable
        );
    }

    @GetMapping("/{orderId}")
    public AdminOrderResponse getOrder(
            @PathVariable Long orderId
    ) {

        return adminOrderService.getOrder(orderId);
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<AdminOrderResponse> updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody
            UpdateOrderStatusRequest request
    ) {

        return ResponseEntity.ok(
                adminOrderService.updateStatus(
                        orderId,
                        request.status()
                )
        );
    }
}
