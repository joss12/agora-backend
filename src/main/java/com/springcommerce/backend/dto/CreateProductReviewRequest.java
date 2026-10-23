package com.springcommerce.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductReviewRequest(

        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating cannot exceed 5")
        Integer rating,

        @Size(max = 150, message = "Title cannot exceed 150 characters")
        String title,

        @NotBlank(message = "Review content is required")
        String content
) {}