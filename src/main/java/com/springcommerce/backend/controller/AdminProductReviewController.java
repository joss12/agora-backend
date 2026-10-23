package com.springcommerce.backend.controller;

import com.springcommerce.backend.service.ProductReviewService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminProductReviewController {

    private final ProductReviewService reviewService;

    public AdminProductReviewController(
            ProductReviewService reviewService
    ) {
        this.reviewService = reviewService;
    }

    // =========================================================
    // ADMIN DELETE REVIEW
    // =========================================================

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId
    ) {

        reviewService.adminDeleteReview(reviewId);

        return ResponseEntity.noContent().build();
    }
}
