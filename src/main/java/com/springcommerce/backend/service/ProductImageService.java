package com.springcommerce.backend.service;

import com.cloudinary.Cloudinary;
import com.springcommerce.backend.dto.ProductImageResponse;
import com.springcommerce.backend.entity.Product;
import com.springcommerce.backend.entity.ProductImage;
import com.springcommerce.backend.exception.ProductNotFoundException;
import com.springcommerce.backend.repository.ProductImageRepository;
import com.springcommerce.backend.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductImageService {
    private static final long MAX_FILE_SIZE =
            5L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final Cloudinary cloudinary;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;

    public ProductImageService(
            Cloudinary cloudinary,
            ProductRepository productRepository,
            ProductImageRepository imageRepository
    ) {
        this.cloudinary = cloudinary;
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImages(
            Long productId
    ) {
        requireProduct(productId);

        return imageRepository
                .findByProductIdOrderByDisplayOrderAscIdAsc(
                        productId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(
            cacheNames = "products",
            allEntries = true
    )
    public ProductImageResponse upload(
            Long productId,
            MultipartFile file
    ) {
        validateFile(file);

        Product product = requireProduct(productId);

        Map<?, ?> uploadResult;

        try {
            uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    Map.of(
                            "folder", "spring-commerce/products",
                            "resource_type", "image",
                            "overwrite", false
                    )
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary upload failed",
                    e
            );
        }

        String publicId =
                (String) uploadResult.get("public_id");

        String secureUrl =
                (String) uploadResult.get("secure_url");

        if (publicId == null || secureUrl == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary returned an incomplete response"
            );
        }

        try {
            List<ProductImage> existingImages =
                    imageRepository
                            .findByProductIdOrderByDisplayOrderAscIdAsc(
                                    productId
                            );

            ProductImage image = new ProductImage();

            image.setProduct(product);
            image.setPublicId(publicId);
            image.setImageUrl(secureUrl);
            image.setPrimary(existingImages.isEmpty());
            image.setDisplayOrder(existingImages.size());

            return toResponse(
                    imageRepository.saveAndFlush(image)
            );

        } catch (RuntimeException databaseError) {
            // The upload succeeded, but database persistence failed.
            // Attempt to remove the orphaned Cloudinary image.
            try {
                cloudinary.uploader().destroy(
                        publicId,
                        Map.of(
                                "resource_type",
                                "image"
                        )
                );
            } catch (Exception cleanupError) {
                databaseError.addSuppressed(cleanupError);
            }

            throw databaseError;
        }
    }

    @Transactional
    @CacheEvict(
            cacheNames = "products",
            allEntries = true
    )
    public ProductImageResponse setPrimary(
            Long productId,
            Long imageId
    ) {
        requireProduct(productId);

        ProductImage selectedImage = imageRepository
                .findByIdAndProductId(
                        imageId,
                        productId
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product image not found"
                        )
                );

        List<ProductImage> images = imageRepository
                .findByProductIdOrderByDisplayOrderAscIdAsc(
                        productId
                );

        for (ProductImage image : images) {
            image.setPrimary(
                    image.getId().equals(
                            selectedImage.getId()
                    )
            );
        }

        imageRepository.saveAllAndFlush(images);

        return toResponse(selectedImage);
    }

    @Transactional
    @CacheEvict(
            cacheNames = "products",
            allEntries = true
    )
    public void delete(
            Long productId,
            Long imageId
    ) {
        requireProduct(productId);

        ProductImage image = imageRepository
                .findByIdAndProductId(
                        imageId,
                        productId
                )
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product image not found"
                        )
                );

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                    image.getPublicId(),
                    Map.of(
                            "resource_type",
                            "image"
                    )
            );

            if (!"ok".equals(result.get("result"))
                    && !"not found".equals(result.get("result"))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Cloudinary could not delete the image"
                );
            }

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudinary deletion failed",
                    e
            );
        }

        boolean wasPrimary = image.isPrimary();

        imageRepository.delete(image);
        imageRepository.flush();

        if (wasPrimary) {
            List<ProductImage> remainingImages =
                    imageRepository
                            .findByProductIdOrderByDisplayOrderAscIdAsc(
                                    productId
                            );

            if (!remainingImages.isEmpty()) {
                ProductImage nextPrimary =
                        remainingImages.getFirst();

                nextPrimary.setPrimary(true);
                imageRepository.save(nextPrimary);
            }
        }
    }

    private Product requireProduct(Long productId) {
        return productRepository
                .findById(productId)
                .orElseThrow(
                        () -> new ProductNotFoundException(
                                productId
                        )
                );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An image file is required"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must not exceed 5 MB"
            );
        }

        if (!ALLOWED_TYPES.contains(
                file.getContentType()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG and WebP are supported"
            );
        }
    }

    private ProductImageResponse toResponse(
            ProductImage image
    ) {
        String thumbnailUrl = cloudinary
                .url()
                .secure(true)
                .resourceType("image")
                .transformation(
                        new com.cloudinary.Transformation<>()
                                .width(400)
                                .height(400)
                                .crop("fill")
                                .quality("auto")
                                .fetchFormat("auto")
                )
                .generate(image.getPublicId());

        return ProductImageResponse.from(
                image,
                thumbnailUrl
        );
    }
}
