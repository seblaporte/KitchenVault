package fr.seblaporte.mycookidoo.service;

import fr.seblaporte.mycookidoo.entity.Recipe;
import fr.seblaporte.mycookidoo.repository.RecipeRepository;
import fr.seblaporte.mycookidoo.repository.RecipeSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;

    public RecipeService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public Page<Recipe> listRecipes(String search, List<String> categoryIds, List<String> difficulties,
                                     Integer maxTotalTimeMinutes, Pageable pageable) {
        Specification<Recipe> spec = Specification.where(null);

        if (search != null && !search.isBlank()) {
            spec = spec.and(RecipeSpecification.nameContains(search.trim()));
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            spec = spec.and(RecipeSpecification.inCategories(categoryIds));
        }
        if (difficulties != null && !difficulties.isEmpty()) {
            spec = spec.and(RecipeSpecification.inDifficulties(difficulties));
        }
        if (maxTotalTimeMinutes != null) {
            spec = spec.and(RecipeSpecification.maxTotalTime(maxTotalTimeMinutes));
        }

        return recipeRepository.findAll(spec, pageable);
    }

    public Optional<Recipe> getRecipeById(String id) {
        return recipeRepository.findById(id);
    }
}
