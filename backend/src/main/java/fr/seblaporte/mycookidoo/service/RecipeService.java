package fr.seblaporte.mycookidoo.service;

import fr.seblaporte.mycookidoo.entity.Recipe;
import fr.seblaporte.mycookidoo.repository.RecipeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;

    public RecipeService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public Page<Recipe> listRecipes(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return recipeRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        }
        return recipeRepository.findAll(pageable);
    }

    public Optional<Recipe> getRecipeById(String id) {
        return recipeRepository.findByIdWithIngredients(id);
    }
}
