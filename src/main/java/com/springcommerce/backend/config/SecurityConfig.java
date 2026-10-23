package com.springcommerce.backend.config;

import com.springcommerce.backend.security.JwtAuthenticationFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exceptions -> exceptions

                        .authenticationEntryPoint(
                                (request, response, exception) ->
                                        response.sendError(
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "Authentication required"
                                        )
                        )

                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        response.sendError(
                                                HttpServletResponse.SC_FORBIDDEN,
                                                "Access denied"
                                        )
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // =================================================
                        // ERROR DISPATCHES
                        // =================================================

                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        )
                        .permitAll()

                        // =================================================
                        // SWAGGER UI / OPENAPI DOCUMENTATION
                        // =================================================

                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        // =================================================
                        // AUTHENTICATION
                        // =================================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me"
                        )
                        .authenticated()

                        // =================================================
                        // CUSTOMER ACCOUNT
                        // =================================================

                        .requestMatchers(
                                "/api/account",
                                "/api/account/**"
                        )
                        .authenticated()

                        // =================================================
                        // PRODUCT REVIEWS — ADMIN
                        // =================================================

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/admin/reviews/*"
                        )
                        .hasRole("ADMIN")

                        // =================================================
                        // PRODUCT REVIEWS — PUBLIC READ ACCESS
                        // =================================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products/*/reviews",
                                "/api/v1/products/*/ratings"
                        )
                        .permitAll()

                        // =================================================
                        // PRODUCT REVIEWS — AUTHENTICATED WRITES
                        // =================================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/products/*/reviews"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/reviews/*"
                        )
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/reviews/*"
                        )
                        .authenticated()

                        // =================================================
                        // ADMIN ROUTES
                        // =================================================

                        .requestMatchers(
                                "/api/admin",
                                "/api/admin/**"
                        )
                        .hasRole("ADMIN")

                        // =================================================
                        // PUBLIC PRODUCT CATALOG
                        // =================================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products",
                                "/api/products/*"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products/*/images"
                        )
                        .permitAll()

                        // =================================================
                        // PRODUCT IMAGE MANAGEMENT
                        // =================================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products/*/images"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/products/*/images/*/primary"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/products/*/images/*"
                        )
                        .hasRole("ADMIN")

                        // =================================================
                        // PUBLIC CATEGORIES
                        // =================================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/categories",
                                "/api/categories/*"
                        )
                        .permitAll()

                        // =================================================
                        // ADMIN PRODUCT MANAGEMENT
                        // =================================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/products/*"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/products/*"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/products/*/stock/**",
                                "/api/products/*/inventory-history"
                        )
                        .hasRole("ADMIN")

                        // =================================================
                        // ADMIN CATEGORY MANAGEMENT
                        // =================================================

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/categories"
                        )
                        .hasRole("ADMIN")

                        // =================================================
                        // WISHLIST — AUTHENTICATED CUSTOMERS
                        // =================================================

                        .requestMatchers(
                                "/api/v1/wishlist",
                                "/api/v1/wishlist/**"
                        )
                        .authenticated()

                        // =================================================
                        // SHOPPING CART
                        // =================================================

                        .requestMatchers(
                                "/api/cart",
                                "/api/cart/**"
                        )
                        .authenticated()

                        // =================================================
                        // CUSTOMER ORDERS
                        // =================================================

                        .requestMatchers(
                                "/api/orders",
                                "/api/orders/**"
                        )
                        .hasAnyRole("CUSTOMER", "ADMIN")

                        // =================================================
                        // DEFAULT SECURITY
                        // =================================================

                        .anyRequest()
                        .authenticated()
                )

                // =================================================
                // JWT AUTHENTICATION FILTER
                // =================================================

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}