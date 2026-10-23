package com.springcommerce.backend.dto;

import java.util.List;

public record ProductPageResponse(
        List<ProductResponse> products,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
