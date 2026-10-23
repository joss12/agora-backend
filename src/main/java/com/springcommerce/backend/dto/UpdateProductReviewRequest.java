package com.springcommerce.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProductReviewRequest(

        @Min(1)
        @Max(5)
        Integer rating,

        @Size(max = 150)
        String title,

        @NotBlank
        String content
) {}