
package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.LoginRequest;
import com.springcommerce.backend.dto.LoginResponse;
import com.springcommerce.backend.dto.RegisterRequest;
import com.springcommerce.backend.dto.UserResponse;

import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.entity.UserRole;

import com.springcommerce.backend.exception.DuplicateEmailException;
import com.springcommerce.backend.exception.InvalidCredentialsException;

import com.springcommerce.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }



    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        User user = new User();

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);

        // Never store a plaintext password.
        user.setPassword(
                passwordEncoder.encode(request.password())
        );

        // Public registration cannot create administrator accounts.
        user.setRole(UserRole.CUSTOMER);
        user.setEnabled(true);

        User savedUser = userRepository.save(user);

        return mapToResponse(savedUser);
    }



    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(
                        InvalidCredentialsException::new
                );

        // Use the same error for unknown emails and wrong passwords.
        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new InvalidCredentialsException();
        }

        // Disabled accounts must not receive new access tokens.
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateToken(user);

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getRole().name()
        );
    }



    private UserResponse mapToResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
