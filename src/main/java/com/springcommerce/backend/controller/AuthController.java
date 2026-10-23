package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.RegisterRequest;
import com.springcommerce.backend.dto.UserResponse;
import com.springcommerce.backend.exception.InvalidCredentialsException;
import com.springcommerce.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.springcommerce.backend.dto.LoginRequest;
import com.springcommerce.backend.dto.LoginResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {

        UserResponse response =
                userService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ){
        LoginResponse response = userService.login(request);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(
            InvalidCredentialsException exception
    ){
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(exception.getMessage());
    }
}