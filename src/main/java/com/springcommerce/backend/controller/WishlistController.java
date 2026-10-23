package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.WishlistResponse;
import com.springcommerce.backend.service.WishlistService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public WishlistResponse getWishlist(
            Authentication authentication
    ) {
        return wishlistService.getWishlist(
                authentication.getName()
        );
    }

    @PostMapping("/{productId}")
    public WishlistResponse addProduct(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        return wishlistService.addProduct(
                authentication.getName(),
                productId
        );
    }

    @DeleteMapping("/{productId}")
    public WishlistResponse removeProduct(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        return wishlistService.removeProduct(
                authentication.getName(),
                productId
        );
    }

    @GetMapping("/count")
    public Map<String, Long> count(
            Authentication authentication
    ) {
        return Map.of(
                "count",
                wishlistService.count(authentication.getName())
        );
    }

    @GetMapping("/products/{productId}")
    public Map<String, Boolean> isSaved(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        return Map.of(
                "saved",
                wishlistService.isSaved(
                        authentication.getName(),
                        productId
                )
        );
    }
}