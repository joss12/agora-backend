package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.CreateProductReviewRequest;
import com.springcommerce.backend.dto.ProductRatingSummaryResponse;
import com.springcommerce.backend.dto.ProductReviewResponse;
import com.springcommerce.backend.dto.UpdateProductReviewRequest;
import com.springcommerce.backend.service.ProductReviewService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProductReviewController {

    private final ProductReviewService reviewService;

    public ProductReviewController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // =========================================================
    // CREATE REVIEW
    // =========================================================

    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<ProductReviewResponse> createReview(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductReviewRequest request
    ) {

        ProductReviewResponse response =
                reviewService.createReview(
                        authentication.getName(),
                        productId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =========================================================
    // LIST PRODUCT REVIEWS
    // =========================================================

    @GetMapping("/products/{productId}/reviews")
    public Page<ProductReviewResponse> getProductReviews(
            @PathVariable Long productId,
            Pageable pageable
    ) {

        return reviewService.getProductReviews(
                productId,
                pageable
        );
    }

    // =========================================================
    // PRODUCT RATING SUMMARY
    // =========================================================

    @GetMapping("/products/{productId}/ratings")
    public ProductRatingSummaryResponse getRatingSummary(
            @PathVariable Long productId
    ) {

        return reviewService.getRatingSummary(productId);
    }

    // =========================================================
    // UPDATE OWN REVIEW
    // =========================================================

    @PutMapping("/reviews/{reviewId}")
    public ProductReviewResponse updateReview(
            Authentication authentication,
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateProductReviewRequest request
    ) {

        return reviewService.updateReview(
                authentication.getName(),
                reviewId,
                request
        );
    }

    // =========================================================
    // DELETE OWN REVIEW
    // =========================================================

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            Authentication authentication,
            @PathVariable Long reviewId
    ) {

        reviewService.deleteReview(
                authentication.getName(),
                reviewId
        );

        return ResponseEntity.noContent().build();
    }
}
