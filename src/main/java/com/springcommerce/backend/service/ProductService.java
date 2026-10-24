package com.springcommerce.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;

import com.springcommerce.backend.dto.CategorySummary;
import com.springcommerce.backend.dto.CreateProductRequest;
import com.springcommerce.backend.dto.InventoryMovementResponse;
import com.springcommerce.backend.dto.ProductImageResponse;
import com.springcommerce.backend.dto.ProductPageResponse;
import com.springcommerce.backend.dto.ProductResponse;
import com.springcommerce.backend.dto.StockRequest;
import com.springcommerce.backend.dto.UpdateProductRequest;

import com.springcommerce.backend.entity.Category;
import com.springcommerce.backend.entity.InventoryMovement;
import com.springcommerce.backend.entity.InventoryMovementType;
import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.ProductImage;

import com.springcommerce.backend.exception.CategoryNotFoundException;
import com.springcommerce.backend.exception.DuplicateSkuException;
import com.springcommerce.backend.exception.InsufficientStockException;
import com.springcommerce.backend.exception.ProductNotFoundException;

import com.springcommerce.backend.repository.CategoryRepository;
import com.springcommerce.backend.repository.InventoryMovementRepository;
import com.springcommerce.backend.repository.ProductImageRepository;
import com.springcommerce.backend.repository.ProductRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ProductImageRepository imageRepository;
    private final Cloudinary cloudinary;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            InventoryMovementRepository inventoryMovementRepository,
            ProductImageRepository imageRepository,
            Cloudinary cloudinary
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.imageRepository = imageRepository;
        this.cloudinary = cloudinary;
    }

    // =========================================================
    // CREATE PRODUCT
    // =========================================================

    @Transactional
    public ProductResponse createProduct(
            CreateProductRequest request
    ) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Category category = categoryRepository
                .findById(request.categoryId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                request.categoryId()
                        )
                );

        Product product = new Product();

        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(category);
        product.setActive(true);

        Product savedProduct =
                productRepository.save(product);

        return mapToResponse(savedProduct);
    }

    // =========================================================
    // GET ALL PRODUCTS
    // =========================================================

    @Transactional(readOnly = true)
    public ProductPageResponse getAllProducts(
            int page,
            int size,
            String search,
            Long categoryId,
            String sort,
            String direction
    ) {
        Sort.Direction sortDirection =
                direction.equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Sort sorting =
                Sort.by(sortDirection, sort);

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        sorting
                );

        Page<Product> products;

        boolean hasSearch =
                search != null && !search.isBlank();

        boolean hasCategory =
                categoryId != null;

        if (hasSearch && hasCategory) {

            products = productRepository
                    .findByNameContainingIgnoreCaseAndCategoryIdAndActiveTrue(
                            search,
                            categoryId,
                            pageable
                    );

        } else if (hasSearch) {

            products = productRepository
                    .findByNameContainingIgnoreCaseAndActiveTrue(
                            search,
                            pageable
                    );

        } else if (hasCategory) {

            products = productRepository
                    .findByCategoryIdAndActiveTrue(
                            categoryId,
                            pageable
                    );

        } else {

            products = productRepository
                    .findByActiveTrue(pageable);
        }

        List<ProductResponse> productResponses =
                products.getContent()
                        .stream()
                        .map(this::mapToResponse)
                        .toList();

        return new ProductPageResponse(
                productResponses,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.isFirst(),
                products.isLast()
        );
    }

    // =========================================================
    // GET PRODUCT BY ID
    // =========================================================

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        Product product = productRepository
                .findByIdAndActiveTrue(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(id)
                );

        return mapToResponse(product);
    }

    // =========================================================
    // UPDATE PRODUCT
    // =========================================================

    @Transactional
    public ProductResponse updateProduct(
            Long id,
            UpdateProductRequest request
    ) {
        Product existingProduct = productRepository
                .findByIdAndActiveTrue(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(id)
                );

        if (productRepository.existsBySkuAndIdNot(
                request.sku(),
                id
        )) {
            throw new DuplicateSkuException(request.sku());
        }

        Category category = categoryRepository
                .findById(request.categoryId())
                .orElseThrow(() ->
                        new CategoryNotFoundException(
                                request.categoryId()
                        )
                );

        existingProduct.setName(request.name());
        existingProduct.setSku(request.sku());
        existingProduct.setDescription(request.description());
        existingProduct.setPrice(request.price());
        existingProduct.setStock(request.stock());
        existingProduct.setCategory(category);

        Product savedProduct =
                productRepository.save(existingProduct);

        return mapToResponse(savedProduct);
    }

    // =========================================================
    // DELETE PRODUCT (SOFT DELETE)
    // =========================================================

    @Transactional
    public void deleteProduct(Long id) {

        Product existingProduct = productRepository
                .findByIdAndActiveTrue(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(id)
                );

        existingProduct.setActive(false);

        productRepository.save(existingProduct);
    }

    // =========================================================
    // ADD STOCK
    // =========================================================

    @Transactional
    public ProductResponse addStock(
            Long productId,
            StockRequest request
    ) {
        Product product = productRepository
                .findByIdAndActiveTrue(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        int stockBefore = product.getStock();

        int stockAfter =
                stockBefore + request.quantity();

        product.setStock(stockAfter);

        Product savedProduct =
                productRepository.save(product);

        InventoryMovement movement =
                new InventoryMovement();

        movement.setProduct(savedProduct);
        movement.setType(
                InventoryMovementType.STOCK_IN
        );
        movement.setQuantity(
                request.quantity()
        );
        movement.setStockBefore(stockBefore);
        movement.setStockAfter(stockAfter);

        inventoryMovementRepository.save(movement);

        return mapToResponse(savedProduct);
    }

    // =========================================================
    // REMOVE STOCK
    // =========================================================

    @Transactional
    public ProductResponse removeStock(
            Long productId,
            StockRequest request
    ) {
        Product product = productRepository
                .findByIdAndActiveTrue(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId)
                );

        int stockBefore = product.getStock();
        int requested = request.quantity();

        if (requested > stockBefore) {
            throw new InsufficientStockException(
                    productId,
                    stockBefore,
                    requested
            );
        }

        int stockAfter =
                stockBefore - requested;

        product.setStock(stockAfter);

        Product savedProduct =
                productRepository.save(product);

        InventoryMovement movement =
                new InventoryMovement();

        movement.setProduct(savedProduct);
        movement.setType(
                InventoryMovementType.STOCK_OUT
        );
        movement.setQuantity(requested);
        movement.setStockBefore(stockBefore);
        movement.setStockAfter(stockAfter);

        inventoryMovementRepository.save(movement);

        return mapToResponse(savedProduct);
    }

    // =========================================================
    // INVENTORY HISTORY
    // =========================================================

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> getInventoryHistory(
            Long productId
    ) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }

        return inventoryMovementRepository
                .findByProductIdOrderByCreatedAtDesc(
                        productId
                )
                .stream()
                .map(this::mapMovementToResponse)
                .toList();
    }

    // =========================================================
    // GET ARCHIVED PRODUCTS
    // =========================================================

    @Transactional(readOnly = true)
    public ProductPageResponse getArchivedProducts(
            int page,
            int size
    ) {
        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "id"
                        )
                );

        Page<Product> products =
                productRepository.findByActiveFalse(
                        pageable
                );

        List<ProductResponse> responses =
                products.getContent()
                        .stream()
                        .map(this::mapToResponse)
                        .toList();

        return new ProductPageResponse(
                responses,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages(),
                products.isFirst(),
                products.isLast()
        );
    }

    // =========================================================
    // RESTORE ARCHIVED PRODUCT
    // =========================================================

    @Transactional
    public ProductResponse restoreProduct(Long id) {

        Product product = productRepository
                .findByIdAndActiveFalse(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(id)
                );

        product.setActive(true);

        Product restored =
                productRepository.save(product);

        return mapToResponse(restored);
    }

    // =========================================================
    // MAP INVENTORY MOVEMENT TO RESPONSE
    // =========================================================

    private InventoryMovementResponse mapMovementToResponse(
            InventoryMovement movement
    ) {
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getType().name(),
                movement.getQuantity(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getCreatedAt()
        );
    }

    // =========================================================
    // MAP PRODUCT TO RESPONSE
    // =========================================================

    private ProductResponse mapToResponse(
            Product product
    ) {
        CategorySummary category = null;

        if (product.getCategory() != null) {
            category = new CategorySummary(
                    product.getCategory().getId(),
                    product.getCategory().getName()
            );
        }

        List<ProductImageResponse> images =
                getProductImages(product.getId());

        String primaryImageUrl =
                images.stream()
                        .filter(
                                ProductImageResponse::primary
                        )
                        .map(
                                ProductImageResponse::thumbnailUrl
                        )
                        .findFirst()
                        .orElse(null);

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                category,
                product.getCreatedAt(),
                product.getUpdatedAt(),
                primaryImageUrl,
                images
        );
    }

    // =========================================================
    // GET PRODUCT IMAGES
    // =========================================================

    private List<ProductImageResponse> getProductImages(
            Long productId
    ) {
        return imageRepository
                .findByProductIdOrderByDisplayOrderAscIdAsc(
                        productId
                )
                .stream()
                .map(this::mapImageToResponse)
                .toList();
    }

    // =========================================================
    // MAP IMAGE TO RESPONSE
    // =========================================================

    private ProductImageResponse mapImageToResponse(
            ProductImage image
    ) {
        String thumbnailUrl;

        if (
                image.getPublicId() != null
                        && image.getPublicId().startsWith("seed-")
        ) {
            thumbnailUrl = image.getImageUrl();

        } else {

            thumbnailUrl = cloudinary
                    .url()
                    .secure(true)
                    .resourceType("image")
                    .transformation(
                            new Transformation<>()
                                    .width(400)
                                    .height(400)
                                    .crop("fill")
                                    .quality("auto")
                                    .fetchFormat("auto")
                    )
                    .generate(
                            image.getPublicId()
                    );
        }

        return ProductImageResponse.from(
                image,
                thumbnailUrl
        );
    }
}