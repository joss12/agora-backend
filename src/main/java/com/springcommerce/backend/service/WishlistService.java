package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.WishlistItemResponse;
import com.springcommerce.backend.dto.WishlistResponse;

import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.ProductImage;
import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.entity.WishlistItem;

import com.springcommerce.backend.exception.ProductNotFoundException;

import com.springcommerce.backend.repository.ProductImageRepository;
import com.springcommerce.backend.repository.ProductRepository;
import com.springcommerce.backend.repository.UserRepository;
import com.springcommerce.backend.repository.WishlistItemRepository;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final UserRepository userRepository;

    public WishlistService(
            WishlistItemRepository wishlistRepository,
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            UserRepository userRepository
    ) {
        this.wishlistRepository = wishlistRepository;
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public WishlistResponse getWishlist(String email) {

        User user = findUser(email);

        List<WishlistItem> wishlistItems = wishlistRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(
                        user.getId()
                );

        if (wishlistItems.isEmpty()) {
            return new WishlistResponse(List.of(), 0);
        }

        List<Long> productIds = wishlistItems
                .stream()
                .map(item -> item.getProduct().getId())
                .toList();

        Map<Long, ProductImage> imagesByProductId =
                imageRepository
                        .findWishlistImages(productIds)
                        .stream()
                        .collect(Collectors.toMap(
                                image -> image.getProduct().getId(),
                                Function.identity(),
                                (first, ignored) -> first
                        ));

        List<WishlistItemResponse> items = wishlistItems
                .stream()
                .map(item -> toResponse(
                        item,
                        imagesByProductId.get(
                                item.getProduct().getId()
                        )
                ))
                .toList();

        return new WishlistResponse(items, items.size());
    }

    @Transactional
    public WishlistResponse addProduct(
            String email,
            Long productId
    ) {
        User user = findUser(email);

        Product product = productRepository
                .findByIdAndActiveTrue(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        boolean alreadySaved = wishlistRepository
                .existsByUserIdAndProductId(
                        user.getId(),
                        productId
                );

        if (!alreadySaved) {
            WishlistItem item = new WishlistItem();

            item.setUser(user);
            item.setProduct(product);

            wishlistRepository.saveAndFlush(item);
        }

        return getWishlist(email);
    }

    @Transactional
    public WishlistResponse removeProduct(
            String email,
            Long productId
    ) {
        User user = findUser(email);

        wishlistRepository
                .findByUserIdAndProductId(
                        user.getId(),
                        productId
                )
                .ifPresent(wishlistRepository::delete);

        wishlistRepository.flush();

        return getWishlist(email);
    }

    @Transactional(readOnly = true)
    public long count(String email) {

        User user = findUser(email);

        return wishlistRepository.countByUserId(
                user.getId()
        );
    }

    @Transactional(readOnly = true)
    public boolean isSaved(
            String email,
            Long productId
    ) {
        User user = findUser(email);

        return wishlistRepository
                .existsByUserIdAndProductId(
                        user.getId(),
                        productId
                );
    }

    private User findUser(String email) {

        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found."
                        )
                );
    }

    private WishlistItemResponse toResponse(
            WishlistItem item,
            ProductImage image
    ) {
        Product product = item.getProduct();

        String imageUrl =
                image != null
                        ? image.getImageUrl()
                        : null;

        return new WishlistItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                product.getStock(),
                product.isActive(),
                imageUrl,
                item.getCreatedAt()
        );
    }
}