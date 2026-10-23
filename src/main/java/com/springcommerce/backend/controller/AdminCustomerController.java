package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.admin.AdminCustomerDetailResponse;
import com.springcommerce.backend.dto.admin.AdminCustomerPageResponse;
import com.springcommerce.backend.service.AdminCustomerService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

    public AdminCustomerController(
            AdminCustomerService adminCustomerService
    ) {
        this.adminCustomerService =
                adminCustomerService;
    }

    @GetMapping
    public ResponseEntity<AdminCustomerPageResponse> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                adminCustomerService.getCustomers(
                        page,
                        size
                )
        );
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<AdminCustomerDetailResponse> getCustomer(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(
                adminCustomerService.getCustomer(
                        customerId
                )
        );
    }
}