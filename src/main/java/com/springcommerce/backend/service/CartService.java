package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.AddCartItemRequest;
import com.springcommerce.backend.dto.CartItemResponse;
import com.springcommerce.backend.dto.CartResponse;
import com.springcommerce.backend.dto.UpdateCartItemRequest;

import com.springcommerce.backend.entity.Cart;
import com.springcommerce.backend.entity.CartItem;
import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.User;

import com.springcommerce.backend.exception.ProductNotFoundException;

import com.springcommerce.backend.repository.CartItemRepository;
import com.springcommerce.backend.repository.CartRepository;
import com.springcommerce.backend.repository.ProductRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // GET CART
    // =========================================================

    @Transactional(readOnly = true)
    public CartResponse getCart(String email) {

        User user = findUser(email);

        Cart cart = cartRepository
                .findByUserId(user.getId())
                .orElse(null);

        if (cart == null) {
            return new CartResponse(
                    null,
                    List.of(),
                    0,
                    BigDecimal.ZERO
            );
        }

        return buildCartResponse(cart);
    }

    // =========================================================
    // ADD PRODUCT TO CART
    // =========================================================

    @Transactional
    public CartResponse addItem(
            String email,
            AddCartItemRequest request
    ) {

        User user = findUser(email);

        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                request.productId()
                        )
                );

        if (request.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero."
            );
        }

        Cart cart = cartRepository
                .findByUserId(user.getId())
                .orElseGet(() -> createCart(user));

        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductId(
                        cart.getId(),
                        product.getId()
                )
                .orElse(null);

        int currentQuantity =
                cartItem == null
                        ? 0
                        : cartItem.getQuantity();

        long requestedTotal =
                (long) currentQuantity + request.quantity();

        validateStock(product, requestedTotal);

        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
        }

        cartItem.setQuantity((int) requestedTotal);

        cartItemRepository.save(cartItem);

        return buildCartResponse(cart);
    }

    // =========================================================
    // UPDATE CART ITEM
    // =========================================================

    @Transactional
    public CartResponse updateItem(
            String email,
            Long itemId,
            UpdateCartItemRequest request
    ) {

        User user = findUser(email);

        Cart cart = cartRepository
                .findByUserId(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Cart not found."
                        )
                );

        CartItem item = cartItemRepository
                .findByIdAndCartId(
                        itemId,
                        cart.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Cart item not found."
                        )
                );

        if (request.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero."
            );
        }

        validateStock(
                item.getProduct(),
                request.quantity()
        );

        item.setQuantity(request.quantity());

        cartItemRepository.save(item);

        return buildCartResponse(cart);
    }

    // =========================================================
    // REMOVE CART ITEM
    // =========================================================

    @Transactional
    public CartResponse removeItem(
            String email,
            Long itemId
    ) {

        User user = findUser(email);

        Cart cart = cartRepository
                .findByUserId(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Cart not found."
                        )
                );

        CartItem item = cartItemRepository
                .findByIdAndCartId(
                        itemId,
                        cart.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Cart item not found."
                        )
                );

        cartItemRepository.delete(item);
        cartItemRepository.flush();

        return buildCartResponse(cart);
    }

    // =========================================================
    // CLEAR CART
    // =========================================================

    @Transactional
    public void clearCart(String email) {

        User user = findUser(email);

        Cart cart = cartRepository
                .findByUserId(user.getId())
                .orElse(null);

        if (cart == null) {
            return;
        }

        cartItemRepository.deleteByCartId(cart.getId());
    }

    // =========================================================
    // STOCK VALIDATION
    // =========================================================

    private void validateStock(
            Product product,
            long requestedQuantity
    ) {

        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero."
            );
        }

        if (requestedQuantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Requested quantity is too large."
            );
        }

        if (requestedQuantity > product.getStock()) {
            throw new IllegalArgumentException(
                    "Insufficient stock for product: "
                            + product.getName()
                            + ". Available: "
                            + product.getStock()
            );
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private User findUser(String email) {

        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found."
                        )
                );
    }

    private Cart createCart(User user) {

        Cart cart = new Cart();
        cart.setUser(user);

        return cartRepository.save(cart);
    }

    private CartResponse buildCartResponse(Cart cart) {

        List<CartItemResponse> items =
                cartItemRepository
                        .findByCartIdOrderByCreatedAtAsc(
                                cart.getId()
                        )
                        .stream()
                        .map(this::mapItem)
                        .toList();

        int totalQuantity =
                items.stream()
                        .mapToInt(CartItemResponse::quantity)
                        .sum();

        BigDecimal totalPrice =
                items.stream()
                        .map(CartItemResponse::subtotal)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return new CartResponse(
                cart.getId(),
                items,
                totalQuantity,
                totalPrice
        );
    }

    private CartItemResponse mapItem(CartItem item) {

        Product product = item.getProduct();

        BigDecimal subtotal =
                product.getPrice().multiply(
                        BigDecimal.valueOf(
                                item.getQuantity()
                        )
                );

        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                item.getQuantity(),
                subtotal
        );
    }
}