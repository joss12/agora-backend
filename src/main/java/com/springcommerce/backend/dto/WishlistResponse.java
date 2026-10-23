package com.springcommerce.backend.dto;

import java.util.List;

public record WishlistResponse(
        List<WishlistItemResponse> items,
        long count
) {
}
