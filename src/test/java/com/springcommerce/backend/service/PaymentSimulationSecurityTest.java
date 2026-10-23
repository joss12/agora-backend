package com.springcommerce.backend.service;

import com.springcommerce.backend.entity.PaymentOutcome;

import com.springcommerce.backend.repository.OrderRepository;
import com.springcommerce.backend.repository.PaymentAttemptRepository;
import com.springcommerce.backend.repository.PaymentRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSimulationSecurityTest {

    private PaymentService paymentService;

    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private PaymentAttemptRepository attemptRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        attemptRepository = mock(PaymentAttemptRepository.class);
    }

    private PaymentService createService(Environment environment) {
        return new PaymentService(
                userRepository,
                orderRepository,
                paymentRepository,
                attemptRepository,
                environment
        );
    }

    private void assertSimulationBlocked(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);

        paymentService = createService(environment);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.simulate(
                        "test@example.com",
                        1L,
                        PaymentOutcome.SUCCESS
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        // Rejected before accessing the database.
        verifyNoInteractions(
                userRepository,
                orderRepository,
                paymentRepository,
                attemptRepository
        );
    }

    @Test
    void productionProfileBlocksSimulation() {
        assertSimulationBlocked("prod");
    }

    @Test
    void defaultProfileBlocksSimulation() {
        assertSimulationBlocked();
    }

    @Test
    void productionOverridesDevelopmentProfile() {
        assertSimulationBlocked("dev", "prod");
    }

    @Test
    void productionOverridesTestProfile() {
        assertSimulationBlocked("test", "prod");
    }

    @Test
    void developmentProfileAllowsSimulationToReachBusinessLogic() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        paymentService = createService(environment);

        // No user is configured in the mocked repository.
        // Reaching the repository proves the profile guard allowed execution.
        assertThrows(
                org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> paymentService.simulate(
                        "test@example.com",
                        1L,
                        PaymentOutcome.SUCCESS
                )
        );

        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void testProfileAllowsSimulationToReachBusinessLogic() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        paymentService = createService(environment);

        assertThrows(
                org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> paymentService.simulate(
                        "test@example.com",
                        1L,
                        PaymentOutcome.SUCCESS
                )
        );

        verify(userRepository).findByEmail("test@example.com");
    }
}