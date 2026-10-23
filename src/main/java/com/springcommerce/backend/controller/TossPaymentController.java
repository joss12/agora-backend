package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.PaymentResponse;
import com.springcommerce.backend.dto.TossConfirmRequest;
import com.springcommerce.backend.dto.TossPrepareResponse;
import com.springcommerce.backend.dto.TossPaymentStatusResponse;

import com.springcommerce.backend.service.TossPaymentService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/payment/toss")
public class TossPaymentController {

    private final TossPaymentService tossPaymentService;

    public TossPaymentController(
            TossPaymentService tossPaymentService
    ) {
        this.tossPaymentService = tossPaymentService;
    }

    // ============================================================
    // PREPARE TOSS PAYMENT
    // ============================================================

    @PostMapping("/prepare")
    public ResponseEntity<TossPrepareResponse> prepare(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                tossPaymentService.prepare(
                        authentication.getName(),
                        orderId
                )
        );
    }

    // ============================================================
    // CONFIRM TOSS PAYMENT
    // ============================================================

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirm(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody TossConfirmRequest request
    ) {
        return ResponseEntity.ok(
                tossPaymentService.confirm(
                        authentication.getName(),
                        orderId,
                        request
                )
        );
    }

    // ============================================================
    // CHECK TOSS PAYMENT STATUS
    // ============================================================

    @GetMapping("/status")
    public ResponseEntity<TossPaymentStatusResponse> status(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                tossPaymentService.checkPaymentStatus(
                        authentication.getName(),
                        orderId
                )
        );
    }

    // ============================================================
    // RECOVER EXPIRED TOSS PAYMENT
    // ============================================================

    @PostMapping("/recover")
    public ResponseEntity<TossPrepareResponse> recover(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(
                tossPaymentService.recover(
                        authentication.getName(),
                        orderId
                )
        );
    }
}