package com.springcommerce.backend.dto.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedCatalog(
        Metadata metadata,
        List<SeedProduct> products
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            String title,
            Integer productCount,
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedProduct(
            String id,
            String sku,
            String slug,
            String name,
            String brand,
            String category,
            String productType,
            String color,
            String condition,
            String currency,
            BigDecimal price,
            BigDecimal originalPrice,
            Integer discountPercent,
            BigDecimal rating,
            Integer reviewCount,
            Integer stock,
            Boolean inStock,
            Boolean rocketStyleDelivery,
            Boolean freeShipping,
            List<String> images,
            String thumbnail,
            String description,
            List<String> tags
    ) {
    }
}