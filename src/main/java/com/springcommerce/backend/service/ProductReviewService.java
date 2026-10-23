package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.CreateProductReviewRequest;
import com.springcommerce.backend.dto.ProductRatingSummaryResponse;
import com.springcommerce.backend.dto.ProductReviewResponse;
import com.springcommerce.backend.dto.UpdateProductReviewRequest;

import com.springcommerce.backend.entity.OrderStatus;
import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.ProductReview;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.repository.OrderItemRepository;
import com.springcommerce.backend.repository.ProductRepository;
import com.springcommerce.backend.repository.ProductReviewRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductReviewService(
            ProductReviewRepository reviewRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
    }

    // =========================================================
    // CREATE REVIEW
    // =========================================================

    @Transactional
    public ProductReviewResponse createReview(
            String email,
            Long productId,
            CreateProductReviewRequest request
    ) {

        User customer = findCustomer(email);

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        // -----------------------------------------------------
        // VERIFY PURCHASE
        // -----------------------------------------------------

        boolean verifiedPurchase =
                orderItemRepository
                        .existsPurchasedProductWithOrderStatus(
                                customer.getId(),
                                productId,
                                OrderStatus.DELIVERED
                        );

        if (!verifiedPurchase) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only customers who received this product can review it"
            );
        }

        // -----------------------------------------------------
        // PREVENT DUPLICATE REVIEWS
        // -----------------------------------------------------

        boolean alreadyReviewed =
                reviewRepository.existsByUserIdAndProductId(
                        customer.getId(),
                        productId
                );

        if (alreadyReviewed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already reviewed this product"
            );
        }

        // -----------------------------------------------------
        // CREATE REVIEW
        // -----------------------------------------------------

        ProductReview review = new ProductReview();

        review.setUser(customer);
        review.setProduct(product);
        review.setRating(request.rating());
        review.setTitle(request.title());
        review.setContent(request.content());

        ProductReview saved = reviewRepository.save(review);

        return toResponse(saved);
    }

    // =========================================================
    // UPDATE REVIEW
    // =========================================================

    @Transactional
    public ProductReviewResponse updateReview(
            String email,
            Long reviewId,
            UpdateProductReviewRequest request
    ) {

        User customer = findCustomer(email);

        ProductReview review = reviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Review not found"
                ));

        // Only the owner can edit the review.

        if (!review.getUser().getId().equals(customer.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot edit another customer's review"
            );
        }

        review.setRating(request.rating());
        review.setTitle(request.title());
        review.setContent(request.content());

        return toResponse(reviewRepository.save(review));
    }

    // =========================================================
    // DELETE OWN REVIEW
    // =========================================================

    @Transactional
    public void deleteReview(
            String email,
            Long reviewId
    ) {

        User customer = findCustomer(email);

        ProductReview review = reviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Review not found"
                ));

        if (!review.getUser().getId().equals(customer.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot delete another customer's review"
            );
        }

        reviewRepository.delete(review);
    }

    // =========================================================
    // LIST PRODUCT REVIEWS
    // =========================================================

    @Transactional(readOnly = true)
    public Page<ProductReviewResponse> getProductReviews(
            Long productId,
            Pageable pageable
    ) {

        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
            );
        }

        return reviewRepository
                .findByProductId(productId, pageable)
                .map(this::toResponse);
    }

    // =========================================================
    // PRODUCT RATING SUMMARY
    // =========================================================

    @Transactional(readOnly = true)
    public ProductRatingSummaryResponse getRatingSummary(
            Long productId
    ) {

        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
            );
        }

        long reviewCount =
                reviewRepository.countByProductId(productId);

        Double averageRating =
                reviewRepository.findAverageRatingByProductId(
                        productId
                );

        return new ProductRatingSummaryResponse(
                productId,
                averageRating == null ? 0.0 : averageRating,
                reviewCount
        );
    }

    // =========================================================
    // ADMIN DELETE REVIEW
    // =========================================================

    @Transactional
    public void adminDeleteReview(Long reviewId) {

        ProductReview review = reviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Review not found"
                ));

        reviewRepository.delete(review);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private User findCustomer(String email) {

        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));
    }

    private ProductReviewResponse toResponse(ProductReview review) {

        User customer = review.getUser();

        String customerName =
                customer.getFirstName() + " " + customer.getLastName();

        return new ProductReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                customer.getId(),
                customerName,
                review.getRating(),
                review.getTitle(),
                review.getContent(),
                true,
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}