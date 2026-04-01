package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.generated.api.CategoriesApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.CategoryDto;
import fr.seblaporte.kitchenvault.mapper.RecipeMapper;
import fr.seblaporte.kitchenvault.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoriesDelegate implements CategoriesApiDelegate {

    private final CategoryService categoryService;
    private final RecipeMapper recipeMapper;

    public CategoriesDelegate(CategoryService categoryService, RecipeMapper recipeMapper) {
        this.categoryService = categoryService;
        this.recipeMapper = recipeMapper;
    }

    @Override
    public ResponseEntity<List<CategoryDto>> listCategories() {
        List<CategoryDto> dtos = categoryService.listCategories().stream()
                .map(recipeMapper::toCategoryDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
