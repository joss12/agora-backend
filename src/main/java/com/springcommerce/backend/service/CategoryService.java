package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.CategoryRequest;
import com.springcommerce.backend.dto.CategoryResponse;
import com.springcommerce.backend.entity.Category;
import com.springcommerce.backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository){
        this.categoryRepository = categoryRepository;
    }

    public CategoryResponse createCategory(CategoryRequest request){
        Category category = new Category();
        category.setName(request.name());

        Category savedCategory = categoryRepository.save(category);

        return mapToResponse(savedCategory);
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private CategoryResponse mapToResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
