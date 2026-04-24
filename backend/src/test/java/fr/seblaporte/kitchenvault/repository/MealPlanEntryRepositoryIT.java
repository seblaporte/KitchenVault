package fr.seblaporte.kitchenvault.repository;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.Recipe;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MealPlanEntryRepositoryIT {

    @Autowired MealPlanEntryRepository mealPlanEntryRepository;
    @Autowired RecipeRepository recipeRepository;
    @Autowired DataSource dataSource;

    @BeforeEach
    void cleanUp() {
        mealPlanEntryRepository.deleteAllInBatch();
        recipeRepository.deleteAll();
    }

    @Test
    void save_persistsEntryWithSnapshot() {
        Recipe recipe = saveRecipe("r-1", "Tarte aux pommes");
        MealPlanEntry entry = makeEntry(LocalDate.of(2024, 4, 1), MealType.LUNCH, recipe);

        mealPlanEntryRepository.save(entry);

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "meal_plan_entry"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("entry_date").isEqualTo(LocalDate.of(2024, 4, 1).toString())
                    .value("meal_type").isEqualTo("LUNCH")
                    .value("recipe_id").isEqualTo("r-1")
                    .value("recipe_name_snapshot").isEqualTo("Tarte aux pommes")
                    .value("recipe_id_snapshot").isEqualTo("r-1");
    }

    @Test
    void uniqueConstraint_sameDateAndMealType_throwsOnDuplicate() {
        Recipe recipe = saveRecipe("r-1", "Tarte");
        MealPlanEntry e1 = makeEntry(LocalDate.of(2024, 4, 1), MealType.LUNCH, recipe);
        MealPlanEntry e2 = makeEntry(LocalDate.of(2024, 4, 1), MealType.LUNCH, recipe);

        mealPlanEntryRepository.save(e1);
        assertThatThrownBy(() -> mealPlanEntryRepository.saveAndFlush(e2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onDeleteRecipe_setsRecipeIdToNull() {
        Recipe recipe = saveRecipe("r-del", "Recipe to delete");
        MealPlanEntry entry = makeEntry(LocalDate.of(2024, 4, 2), MealType.DINNER, recipe);
        mealPlanEntryRepository.save(entry);

        recipeRepository.deleteById("r-del");

        org.assertj.db.api.Assertions.assertThat(new Table(dataSource, "meal_plan_entry"))
                .hasNumberOfRows(1)
                .row(0)
                    .value("recipe_id").isNull()
                    .value("recipe_name_snapshot").isEqualTo("Recipe to delete")
                    .value("recipe_id_snapshot").isEqualTo("r-del");
    }

    @Test
    void findWeekPlan_returnsEntriesInRange() {
        Recipe recipe = saveRecipe("r-1", "Tarte");
        LocalDate monday = LocalDate.of(2024, 4, 1);
        mealPlanEntryRepository.save(makeEntry(monday, MealType.LUNCH, recipe));
        mealPlanEntryRepository.save(makeEntry(monday.plusDays(2), MealType.DINNER, recipe));
        mealPlanEntryRepository.save(makeEntry(monday.plusDays(8), MealType.LUNCH, recipe));

        List<MealPlanEntry> result = mealPlanEntryRepository.findWeekPlan(monday, monday.plusDays(6));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEntryDate()).isEqualTo(monday);
        assertThat(result.get(1).getEntryDate()).isEqualTo(monday.plusDays(2));
    }

    @Test
    void findRecentRecipeIds_returnsDistinctIds() {
        Recipe r1 = saveRecipe("r-1", "Recipe 1");
        Recipe r2 = saveRecipe("r-2", "Recipe 2");
        LocalDate today = LocalDate.now();

        mealPlanEntryRepository.save(makeEntry(today.minusDays(3), MealType.LUNCH, r1));
        mealPlanEntryRepository.save(makeEntry(today.minusDays(10), MealType.DINNER, r2));
        mealPlanEntryRepository.save(makeEntry(today.minusDays(100), MealType.LUNCH, r1));

        List<String> ids = mealPlanEntryRepository.findRecentRecipeIds(today.minusDays(28), today);

        assertThat(ids).containsExactlyInAnyOrder("r-1", "r-2");
    }

    @Test
    void findByRecipeIdOrderByEntryDateDesc_appliesLimit() {
        Recipe recipe = saveRecipe("r-1", "Tarte");
        for (int i = 1; i <= 5; i++) {
            mealPlanEntryRepository.save(makeEntry(LocalDate.of(2024, 1, i), MealType.LUNCH, recipe));
        }

        List<MealPlanEntry> result = mealPlanEntryRepository.findByRecipeIdOrderByEntryDateDesc("r-1", PageRequest.of(0, 3));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getEntryDate()).isEqualTo(LocalDate.of(2024, 1, 5));
    }

    private Recipe saveRecipe(String id, String name) {
        Recipe recipe = new Recipe(id);
        recipe.setName(name);
        recipe.setLastSyncedAt(Instant.now());
        return recipeRepository.save(recipe);
    }

    private MealPlanEntry makeEntry(LocalDate date, MealType mealType, Recipe recipe) {
        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(date);
        entry.setMealType(mealType);
        entry.setRecipe(recipe);
        entry.setRecipeNameSnapshot(recipe.getName());
        entry.setRecipeIdSnapshot(recipe.getId());
        return entry;
    }
}
