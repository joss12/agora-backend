package com.springcommerce.backend.dto;

import com.springcommerce.backend.entity.ProductImage;

public record ProductImageResponse(
        Long id,
        Long productId,
        String imageUrl,
        String thumbnailUrl,
        boolean primary,
        int displayOrder
) {
    public static ProductImageResponse from(
            ProductImage image,
            String thumbnailUrl
    ){
        return new ProductImageResponse(
                image.getId(),
                image.getProduct().getId(),
                image.getImageUrl(),
                thumbnailUrl,
                image.isPrimary(),
                image.getDisplayOrder()
        );
    }
}
