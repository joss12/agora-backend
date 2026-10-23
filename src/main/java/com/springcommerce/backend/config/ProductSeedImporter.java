package com.springcommerce.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.springcommerce.backend.dto.seed.SeedCatalog;
import com.springcommerce.backend.entity.Category;
import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.ProductImage;
import com.springcommerce.backend.repository.CategoryRepository;
import com.springcommerce.backend.repository.ProductImageRepository;
import com.springcommerce.backend.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ProductSeedImporter implements CommandLineRunner {

    private static final String SEED_FILE =
            "seed/products-seed.json";

    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;

    public ProductSeedImporter(
            ObjectMapper objectMapper,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductImageRepository productImageRepository
    ) {
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productImageRepository = productImageRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        ClassPathResource resource =
                new ClassPathResource(SEED_FILE);

        if (!resource.exists()) {
            System.out.printf(
                    "Product seed file '%s' not found. Skipping catalog import.%n",
                    SEED_FILE
            );
            return;
        }

        SeedCatalog catalog;

        try (InputStream inputStream = resource.getInputStream()) {
            catalog = objectMapper.readValue(
                    inputStream,
                    SeedCatalog.class
            );
        }

        if (catalog.products() == null || catalog.products().isEmpty()) {
            System.out.println(
                    "Product seed catalog contains no products."
            );
            return;
        }

        Map<String, Category> categoryCache = new HashMap<>();

        int createdProducts = 0;
        int skippedProducts = 0;
        int createdImages = 0;

        for (SeedCatalog.SeedProduct seed : catalog.products()) {
            if (!isValidSeedProduct(seed)) {
                skippedProducts++;
                continue;
            }

            String sku = seed.sku().trim();

            if (productRepository.existsBySku(sku)) {
                skippedProducts++;
                continue;
            }

            Category category = resolveCategory(
                    seed.category(),
                    categoryCache
            );

            Product product = new Product();
            product.setSku(sku);
            product.setName(seed.name().trim());
            product.setPrice(seed.price());
            product.setStock(normalizeStock(seed.stock()));
            product.setDescription(seed.description());
            product.setCategory(category);
            product.setActive(true);

            Product savedProduct =
                    productRepository.save(product);

            createdProducts++;

            createdImages += createProductImages(
                    savedProduct,
                    seed
            );
        }

        System.out.printf(
                "Agora catalog import complete: %d created, %d skipped, %d images created.%n",
                createdProducts,
                skippedProducts,
                createdImages
        );
    }

    private boolean isValidSeedProduct(
            SeedCatalog.SeedProduct seed
    ) {
        if (seed == null) {
            return false;
        }

        if (seed.sku() == null || seed.sku().isBlank()) {
            return false;
        }

        if (seed.name() == null || seed.name().isBlank()) {
            return false;
        }

        return seed.price() != null;
    }

    private int normalizeStock(Integer stock) {
        if (stock == null) {
            return 0;
        }

        return Math.max(stock, 0);
    }

    private int createProductImages(
            Product product,
            SeedCatalog.SeedProduct seed
    ) {
        List<String> images = seed.images();

        if (images == null || images.isEmpty()) {
            return 0;
        }

        int created = 0;

        for (int index = 0; index < images.size(); index++) {
            String imageUrl = images.get(index);

            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }

            ProductImage image = new ProductImage();

            image.setProduct(product);
            image.setPublicId(
                    "seed-" + seed.sku().trim() + "-" + (index + 1)
            );
            image.setImageUrl(imageUrl.trim());
            image.setPrimary(created == 0);
            image.setDisplayOrder(created);

            productImageRepository.save(image);

            created++;
        }

        return created;
    }

    private Category resolveCategory(
            String categoryName,
            Map<String, Category> categoryCache
    ) {
        String normalizedName =
                categoryName == null || categoryName.isBlank()
                        ? "Other"
                        : categoryName.trim();

        String cacheKey =
                normalizedName.toLowerCase(Locale.ROOT);

        Category cachedCategory =
                categoryCache.get(cacheKey);

        if (cachedCategory != null) {
            return cachedCategory;
        }

        Category category = categoryRepository
                .findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> {
                    Category newCategory = new Category();
                    newCategory.setName(normalizedName);

                    return categoryRepository.save(
                            newCategory
                    );
                });

        categoryCache.put(cacheKey, category);

        return category;
    }
}