package com.springcommerce.backend.security;

import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.repository.UserRepository;
import com.springcommerce.backend.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final CustomUserDetailsService userDetailsService;

    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            UserRepository userRepository
    ) {

        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token =
                authorizationHeader.substring(7);

        // 1. Validate JWT signature and expiration.
        if (!jwtService.isTokenValid(token)) {

            filterChain.doFilter(request, response);
            return;
        }

        String email =
                jwtService.extractEmail(token);

        if (email == null || email.isBlank()) {

            filterChain.doFilter(request, response);
            return;
        }

        // 2. Load the current user from MySQL.
        User user = userRepository
                .findByEmail(email)
                .orElse(null);

        if (user == null || !user.isEnabled()) {

            filterChain.doFilter(request, response);
            return;
        }

        // 3. Verify the JWT belongs to this user.
        Long tokenUserId =
                jwtService.extractUserId(token);

        if (tokenUserId == null ||
                !tokenUserId.equals(user.getId())) {

            filterChain.doFilter(request, response);
            return;
        }

        // 4. Compare JWT version with database version.
        if (!jwtService.isTokenVersionValid(
                token,
                user
        )) {

            // An older JWT is no longer valid.
            filterChain.doFilter(request, response);
            return;
        }

        // 5. Authenticate the user.
        if (SecurityContextHolder
                .getContext()
                .getAuthentication() == null) {

            UserDetails userDetails =
                    userDetailsService
                            .loadUserByUsername(email);

            if (!userDetails.isEnabled()) {

                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}