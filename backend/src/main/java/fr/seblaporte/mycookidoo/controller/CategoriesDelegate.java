package fr.seblaporte.mycookidoo.controller;

import fr.seblaporte.mycookidoo.generated.api.CategoriesApiDelegate;
import fr.seblaporte.mycookidoo.generated.model.CategoryDto;
import fr.seblaporte.mycookidoo.mapper.RecipeMapper;
import fr.seblaporte.mycookidoo.service.CategoryService;
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
