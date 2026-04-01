package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.Recipe;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecipeRepositoryIT {

    @Autowired RecipeRepository recipeRepository;
    @Autowired DataSource dataSource;

    @BeforeEach
    void cleanUp() {
        recipeRepository.deleteAll();
    }

    @Test
    void save_persistsRowInDatabase() {
        recipeRepository.save(makeRecipe("r-1", "Tarte aux pommes"));

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "recipe"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("id").isEqualTo("r-1")
                    .value("name").isEqualTo("Tarte aux pommes");
    }

    @Test
    void findByNameContainingIgnoreCase_matchesCaseInsensitive() {
        recipeRepository.save(makeRecipe("r-1", "Tarte aux pommes"));
        recipeRepository.save(makeRecipe("r-2", "Soupe de légumes"));
        recipeRepository.save(makeRecipe("r-3", "TARTE au citron"));

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "recipe"))
                .hasNumberOfRows(3);

        Page<Recipe> result = recipeRepository.findByNameContainingIgnoreCase("tarte", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(Recipe::getId)
                .containsExactlyInAnyOrder("r-1", "r-3");
    }

    @Test
    void findOutdatedRecipes_returnsOnlyStaleRecipes() {
        Recipe fresh = makeRecipe("r-fresh", "Recette fraîche");
        fresh.setLastSyncedAt(Instant.now());
        Recipe stale = makeRecipe("r-stale", "Vieille recette");
        stale.setLastSyncedAt(Instant.now().minus(48, ChronoUnit.HOURS));

        recipeRepository.save(fresh);
        recipeRepository.save(stale);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "recipe"))
                .hasNumberOfRows(2);

        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Recipe> outdated = recipeRepository.findOutdatedRecipes(threshold);

        assertThat(outdated).hasSize(1);
        assertThat(outdated.get(0).getId()).isEqualTo("r-stale");
    }

    @Test
    void save_sameId_updatesExistingRow() {
        recipeRepository.save(makeRecipe("r-1", "Ancien nom"));
        Recipe updated = makeRecipe("r-1", "Nouveau nom");
        recipeRepository.save(updated);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "recipe"))
                .hasNumberOfRows(1)
                .column("name")
                    .hasValues("Nouveau nom");
    }

    private Recipe makeRecipe(String id, String name) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        recipe.setLastSyncedAt(Instant.now());
        return recipe;
    }
}
