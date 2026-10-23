package com.springcommerce.backend.controller;


import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class CurrentUserController {

    private final UserRepository userRepository;

    public CurrentUserController(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
            Authentication authentication
    ) {
        if (
                authentication == null ||
                        !authentication.isAuthenticated()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED
                        )
                );

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getRole().name()
        );
    }

    public record CurrentUserResponse(
            Long id,
            String email,
            String firstName,
            String role
    ) {}
}