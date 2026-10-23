package com.springcommerce.backend.controller;


import com.springcommerce.backend.dto.CategoryRequest;
import com.springcommerce.backend.dto.CategoryResponse;
import com.springcommerce.backend.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request
            ){
        CategoryResponse response = categoryService.createCategory(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public List <CategoryResponse> getAllCategories(){
        return categoryService.getAllCategories();
    }
}
