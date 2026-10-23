package com.springcommerce.backend.dto.admin;

import java.util.List;

public record AdminCustomerPageResponse(
        List<AdminCustomerResponse> customers,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
