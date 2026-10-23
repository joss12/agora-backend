package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.AdminOrderResponse;
import com.springcommerce.backend.dto.admin.AdminCustomerDetailResponse;
import com.springcommerce.backend.dto.admin.AdminCustomerPageResponse;
import com.springcommerce.backend.dto.admin.AdminCustomerResponse;
import com.springcommerce.backend.entity.PaymentStatus;
import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.entity.UserRole;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminCustomerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final AdminOrderService adminOrderService;

    public AdminCustomerService(
            UserRepository userRepository,
            AdminOrderService adminOrderService
    ) {
        this.userRepository = userRepository;
        this.adminOrderService = adminOrderService;
    }

    @Transactional(readOnly = true)
    public AdminCustomerPageResponse getCustomers(
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);

        int safeSize = Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
        );

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize
        );

        Page<AdminCustomerResponse> customers =
                userRepository.findCustomerSummaries(
                        UserRole.CUSTOMER,
                        PaymentStatus.PAID,
                        pageable
                );

        return new AdminCustomerPageResponse(
                customers.getContent(),
                customers.getNumber(),
                customers.getSize(),
                customers.getTotalElements(),
                customers.getTotalPages(),
                customers.isFirst(),
                customers.isLast()
        );
    }

    @Transactional(readOnly = true)
    public AdminCustomerDetailResponse getCustomer(
            Long customerId
    ) {
        User customer = userRepository
                .findByIdAndRole(
                        customerId,
                        UserRole.CUSTOMER
                )
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Customer not found: "
                                        + customerId
                        )
                );

        List<AdminOrderResponse> orders =
                adminOrderService.getOrdersByCustomer(
                        customerId
                );

        long orderCount = orders.size();

        long paidOrderCount = orders.stream()
                .filter(order ->
                        "PAID".equals(
                                order.paymentStatus()
                        )
                )
                .count();

        BigDecimal totalSpent = orders.stream()
                .filter(order ->
                        "PAID".equals(
                                order.paymentStatus()
                        )
                )
                .map(AdminOrderResponse::totalAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        LocalDateTime lastOrderAt = orders.stream()
                .map(AdminOrderResponse::createdAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new AdminCustomerDetailResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.isEnabled(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                orderCount,
                paidOrderCount,
                totalSpent,
                lastOrderAt,
                orders
        );
    }
}