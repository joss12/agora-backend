package com.springcommerce.backend.controller;


import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.CartResponse;
import com.springcommerce.backend.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.springcommerce.backend.dto.UpdateCartItemRequest;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService){
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(Authentication authentication){
        return cartService.getCart(
                authentication.getName()
        );
    }


    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request
            ){
        CartResponse response = cartService.addItem(authentication.getName(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(
            Authentication authentication,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {

        return cartService.updateItem(
                authentication.getName(),
                itemId,
                request
        );
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(
            Authentication authentication,
            @PathVariable Long itemId
    ) {

        return cartService.removeItem(
                authentication.getName(),
                itemId
        );
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            Authentication authentication
    ) {

        cartService.clearCart(
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }
}
