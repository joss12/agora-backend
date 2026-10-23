
package com.springcommerce.backend.controller;

import com.springcommerce.backend.dto.*;
import com.springcommerce.backend.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse response =
                productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ProductPageResponse getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return productService.getAllProducts(
                page,
                size,
                search,
                categoryId,
                sort,
                direction
        );
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(
            @PathVariable Long id
    ) {
        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.updateProduct(id, request);
    }

    @PostMapping("/{id}/stock/in")
    public ProductResponse addStock(
            @PathVariable Long id,
            @Valid @RequestBody StockRequest request
    ) {
        return productService.addStock(id, request);
    }

    @PostMapping("/{id}/stock/out")
    public ProductResponse removeStock(
            @PathVariable Long id,
            @Valid @RequestBody StockRequest request
    ) {
        return productService.removeStock(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id
    ) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/inventory-history")
    public List<InventoryMovementResponse> getInventoryHistory(
            @PathVariable Long id
    ) {
        return productService.getInventoryHistory(id);
    }
}
