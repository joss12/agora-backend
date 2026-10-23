package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.CouponRequest;
import com.springcommerce.backend.dto.CouponResponse;
import com.springcommerce.backend.dto.CouponStatusRequest;
import com.springcommerce.backend.service.CouponService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping
    public ResponseEntity<CouponResponse> create(
            @Valid @RequestBody CouponRequest request
    ) {

        CouponResponse response = couponService.create(request);

        return ResponseEntity
                .created(URI.create(
                        "/api/admin/coupons/" + response.id()
                ))
                .body(response);
    }

    @GetMapping
    public List<CouponResponse> findAll() {
        return couponService.findAll();
    }

    @GetMapping("/{id}")
    public CouponResponse findById(
            @PathVariable Long id
    ) {
        return couponService.findById(id);
    }

    @PutMapping("/{id}")
    public CouponResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CouponRequest request
    ) {
        return couponService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public CouponResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody CouponStatusRequest request
    ) {
        return couponService.updateStatus(id, request.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {

        couponService.delete(id);

        return ResponseEntity.noContent().build();
    }
}