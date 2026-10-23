package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.UserResponse;
import com.springcommerce.backend.dto.account.AddressRequest;
import com.springcommerce.backend.dto.account.AddressResponse;
import com.springcommerce.backend.dto.account.UpdateProfileRequest;
import com.springcommerce.backend.service.AccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(
            AccountService accountService
    ) {
        this.accountService =
                accountService;
    }

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                accountService.getProfile(
                        authentication.getName()
                )
        );
    }

    @PatchMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody
            UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(
                accountService.updateProfile(
                        authentication.getName(),
                        request
                )
        );
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<AddressResponse>> getAddresses(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                accountService.getAddresses(
                        authentication.getName()
                )
        );
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> createAddress(
            Authentication authentication,
            @Valid @RequestBody
            AddressRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        accountService.createAddress(
                                authentication.getName(),
                                request
                        )
                );
    }

    @PatchMapping("/addresses/{addressId}")
    public ResponseEntity<AddressResponse> updateAddress(
            Authentication authentication,
            @PathVariable Long addressId,
            @Valid @RequestBody
            AddressRequest request
    ) {
        return ResponseEntity.ok(
                accountService.updateAddress(
                        authentication.getName(),
                        addressId,
                        request
                )
        );
    }

    @PatchMapping(
            "/addresses/{addressId}/default"
    )
    public ResponseEntity<AddressResponse> setDefaultAddress(
            Authentication authentication,
            @PathVariable Long addressId
    ) {
        return ResponseEntity.ok(
                accountService.setDefaultAddress(
                        authentication.getName(),
                        addressId
                )
        );
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            Authentication authentication,
            @PathVariable Long addressId
    ) {
        accountService.deleteAddress(
                authentication.getName(),
                addressId
        );

        return ResponseEntity.noContent()
                .build();
    }
}