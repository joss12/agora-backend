package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.PaymentResponse;
import com.springcommerce.backend.dto.SimulatePaymentRequest;
import com.springcommerce.backend.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/orders/{orderId}/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final Environment environment;

    public PaymentController(
            PaymentService paymentService,
            Environment environment
    ) {
        this.paymentService = paymentService;
        this.environment = environment;
    }

    @GetMapping
    public PaymentResponse getPayment(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return paymentService.getPayment(
                authentication.getName(),
                orderId
        );
    }

    @PostMapping("/simulate")
    public ResponseEntity<PaymentResponse> simulate(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody SimulatePaymentRequest request
    ) {
        validateSimulationEnvironment();

        return ResponseEntity.ok(
                paymentService.simulate(
                        authentication.getName(),
                        orderId,
                        request.outcome()
                )
        );
    }

    /**
     * Simulated payments are available only in development
     * and testing environments.
     *
     * Production always takes priority when multiple
     * Spring profiles are active.
     */
    private void validateSimulationEnvironment() {

        List<String> activeProfiles =
                Arrays.asList(environment.getActiveProfiles());

        boolean production =
                activeProfiles.contains("prod");

        boolean developmentOrTest =
                activeProfiles.contains("dev")
                        || activeProfiles.contains("test");

        if (production || !developmentOrTest) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Simulated payments are disabled in this environment."
            );
        }
    }
}