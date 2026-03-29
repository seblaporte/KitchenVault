package fr.seblaporte.mycookidoo.service;

import fr.seblaporte.mycookidoo.entity.Recipe;
import fr.seblaporte.mycookidoo.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock RecipeRepository recipeRepository;
    @InjectMocks RecipeService recipeService;

    @Test
    void listRecipes_withoutSearch_callsFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Recipe> page = new PageImpl<>(List.of(makeRecipe("r-1")));
        when(recipeRepository.findAll(pageable)).thenReturn(page);

        Page<Recipe> result = recipeService.listRecipes(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(recipeRepository).findAll(pageable);
    }

    @Test
    void listRecipes_withSearch_callsFindByName() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Recipe> page = new PageImpl<>(List.of(makeRecipe("r-1")));
        when(recipeRepository.findByNameContainingIgnoreCase(eq("tarte"), any())).thenReturn(page);

        Page<Recipe> result = recipeService.listRecipes("tarte", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(recipeRepository).findByNameContainingIgnoreCase("tarte", pageable);
    }

    @Test
    void listRecipes_withBlankSearch_treatsAsNoSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        when(recipeRepository.findAll(pageable)).thenReturn(Page.empty());

        recipeService.listRecipes("   ", pageable);

        verify(recipeRepository).findAll(pageable);
    }

    @Test
    void getRecipeById_found_returnsRecipe() {
        Recipe recipe = makeRecipe("r-1");
        when(recipeRepository.findByIdWithIngredients("r-1")).thenReturn(Optional.of(recipe));

        Optional<Recipe> result = recipeService.getRecipeById("r-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("r-1");
    }

    @Test
    void getRecipeById_notFound_returnsEmpty() {
        when(recipeRepository.findByIdWithIngredients("unknown")).thenReturn(Optional.empty());

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
