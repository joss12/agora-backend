
package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.ProductImageResponse;
import com.springcommerce.backend.service.ProductImageService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService imageService;

    public ProductImageController(
            ProductImageService imageService
    ) {
        this.imageService = imageService;
    }

    @GetMapping
    public List<ProductImageResponse> getImages(
            @PathVariable Long productId
    ) {
        return imageService.getImages(productId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ProductImageResponse> upload(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file
    ) {
        ProductImageResponse response =
                imageService.upload(productId, file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{imageId}/primary")
    public ProductImageResponse setPrimary(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        return imageService.setPrimary(
                productId,
                imageId
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        imageService.delete(
                productId,
                imageId
        );
    }
}
