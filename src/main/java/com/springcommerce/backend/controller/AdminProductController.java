
package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.ProductPageResponse;
import com.springcommerce.backend.dto.ProductResponse;
import com.springcommerce.backend.service.ProductService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(
            ProductService productService
    ) {
        this.productService = productService;
    }

    @GetMapping("/archived")
    public ProductPageResponse getArchivedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return productService.getArchivedProducts(page, size);
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<ProductResponse> restoreProduct(
            @PathVariable Long id
    ) {
        ProductResponse restored =
                productService.restoreProduct(id);

        return ResponseEntity.ok(restored);
    }
}
