package fr.seblaporte.mycookidoo.service;

import fr.seblaporte.mycookidoo.entity.Recipe;
import fr.seblaporte.mycookidoo.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock RecipeRepository recipeRepository;
    @InjectMocks RecipeService recipeService;

    @Test
    void listRecipes_noFilters_callsFindAllWithNullSpec() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Recipe> page = new PageImpl<>(List.of(makeRecipe("r-1")));
        when(recipeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<Recipe> result = recipeService.listRecipes(null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(recipeRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listRecipes_withSearch_callsFindAllWithSpec() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Recipe> page = new PageImpl<>(List.of(makeRecipe("r-1")));
        when(recipeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<Recipe> result = recipeService.listRecipes("tarte", null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(recipeRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listRecipes_withBlankSearch_treatsAsNoFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        when(recipeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        recipeService.listRecipes("   ", null, null, null, pageable);

        verify(recipeRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listRecipes_withCategoryAndDifficultyFilters_callsFindAllWithSpec() {
        Pageable pageable = PageRequest.of(0, 20);
        when(recipeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        recipeService.listRecipes(null, List.of("cat-1"), List.of("easy"), 30, pageable);

        verify(recipeRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getRecipeById_found_returnsRecipe() {
        Recipe recipe = makeRecipe("r-1");
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(recipe));

        Optional<Recipe> result = recipeService.getRecipeById("r-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("r-1");
    }

    @Test
    void getRecipeById_notFound_returnsEmpty() {
        when(recipeRepository.findById("unknown")).thenReturn(Optional.empty());

        Optional<Recipe> result = recipeService.getRecipeById("unknown");

        assertThat(result).isEmpty();
    }

    private Recipe makeRecipe(String id) {
        Recipe recipe = new Recipe(id);
        recipe.setName("Tarte aux pommes");
        recipe.setLastSyncedAt(Instant.now());
        return recipe;
    }
}
