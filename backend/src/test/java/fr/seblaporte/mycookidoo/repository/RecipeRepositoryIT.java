package fr.seblaporte.mycookidoo.repository;

import fr.seblaporte.mycookidoo.entity.Recipe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class RecipeRepositoryIT {

    @Autowired RecipeRepository recipeRepository;

    @Test
    void save_andFindById_returnsRecipe() {
        Recipe recipe = makeRecipe("r-1", "Tarte aux pommes");
        recipeRepository.save(recipe);

        Optional<Recipe> found = recipeRepository.findById("r-1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Tarte aux pommes");
    }

    @Test
    void findByNameContainingIgnoreCase_matchesCaseInsensitive() {
        recipeRepository.save(makeRecipe("r-1", "Tarte aux pommes"));
        recipeRepository.save(makeRecipe("r-2", "Soupe de légumes"));
        recipeRepository.save(makeRecipe("r-3", "TARTE au citron"));

        Page<Recipe> result = recipeRepository.findByNameContainingIgnoreCase("tarte", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Recipe::getId)
                .containsExactlyInAnyOrder("r-1", "r-3");
    }

    @Test
    void findOutdatedRecipes_returnsOnlyOldRecipes() {
        Recipe fresh = makeRecipe("r-fresh", "Recette fraîche");
        fresh.setLastSyncedAt(Instant.now());

        Recipe stale = makeRecipe("r-stale", "Vieille recette");
        stale.setLastSyncedAt(Instant.now().minus(48, ChronoUnit.HOURS));

        recipeRepository.save(fresh);
        recipeRepository.save(stale);

        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Recipe> outdated = recipeRepository.findOutdatedRecipes(threshold);

        assertThat(outdated).hasSize(1);
        assertThat(outdated.get(0).getId()).isEqualTo("r-stale");
    }

    @Test
    void upsert_existingRecipe_updatesName() {
        recipeRepository.save(makeRecipe("r-1", "Ancien nom"));
        Recipe updated = makeRecipe("r-1", "Nouveau nom");
        recipeRepository.save(updated);

        Optional<Recipe> found = recipeRepository.findById("r-1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Nouveau nom");
        assertThat(recipeRepository.count()).isEqualTo(1);
    }

    private Recipe makeRecipe(String id, String name) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        recipe.setLastSyncedAt(Instant.now());
        return recipe;
    }
}
