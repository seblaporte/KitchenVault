package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReviewServiceTest {

    @Mock MealPlanService mealPlanService;
    @Mock RecipeListService recipeListService;

    @InjectMocks WeeklyReviewService weeklyReviewService;

    private static final LocalDate WEEK_START = LocalDate.of(2026, 5, 4);

    @Test
    void generateReview_onlyIncludesDiscoveryRecipesPlannedAsLunchOrDinner() {
        MealPlanEntry discoveryLunch = entry(WEEK_START, MealType.LUNCH, "r-discovery", "Recette découverte");
        MealPlanEntry favoritesDinner = entry(WEEK_START, MealType.DINNER, "r-favorites", "Recette favorite");
        MealPlanEntry undefinedEntry = entry(WEEK_START, MealType.UNDEFINED, "r-discovery", "Recette découverte");

        when(mealPlanService.getWeekPlan(WEEK_START))
                .thenReturn(List.of(discoveryLunch, favoritesDinner, undefinedEntry));
        when(recipeListService.getRoleOfRecipe("r-discovery")).thenReturn(Optional.of(RecipeListRole.DISCOVERY));
        when(recipeListService.getRoleOfRecipe("r-favorites")).thenReturn(Optional.of(RecipeListRole.FAVORITES));

        List<WeeklyReviewService.Item> items = weeklyReviewService.generateReview(WEEK_START);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).recipeId()).isEqualTo("r-discovery");
        assertThat(items.get(0).plannedDates()).containsExactly(WEEK_START);
    }

    @Test
    void generateReview_groupsMultiplePlannedDatesForSameRecipe() {
        MealPlanEntry monday = entry(WEEK_START, MealType.LUNCH, "r-discovery", "Recette découverte");
        MealPlanEntry wednesday = entry(WEEK_START.plusDays(2), MealType.DINNER, "r-discovery", "Recette découverte");

        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of(monday, wednesday));
        when(recipeListService.getRoleOfRecipe("r-discovery")).thenReturn(Optional.of(RecipeListRole.DISCOVERY));

        List<WeeklyReviewService.Item> items = weeklyReviewService.generateReview(WEEK_START);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).plannedDates()).containsExactly(WEEK_START, WEEK_START.plusDays(2));
    }

    @Test
    void generateReview_excludesUnclassifiedRecipes() {
        MealPlanEntry unclassified = entry(WEEK_START, MealType.LUNCH, "r-unclassified", "Recette");
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of(unclassified));
        when(recipeListService.getRoleOfRecipe("r-unclassified")).thenReturn(Optional.empty());

        assertThat(weeklyReviewService.generateReview(WEEK_START)).isEmpty();
    }

    @Test
    void submitVotes_appliesEachMoveIndependently() {
        when(recipeListService.moveRecipe("r-1", RecipeListRole.FAVORITES)).thenReturn(RecipeListRole.FAVORITES);
        when(recipeListService.moveRecipe("r-2", RecipeListRole.REJECTED)).thenReturn(RecipeListRole.REJECTED);

        Map<String, RecipeListRole> votes = new LinkedHashMap<>();
        votes.put("r-1", RecipeListRole.FAVORITES);
        votes.put("r-2", RecipeListRole.REJECTED);

        WeeklyReviewService.SubmitResult result = weeklyReviewService.submitVotes(votes);

        assertThat(result.moved()).hasSize(2);
        assertThat(result.failed()).isEmpty();
    }

    @Test
    void submitVotes_oneFailureDoesNotPreventOthersFromSucceeding() {
        when(recipeListService.moveRecipe("r-ok", RecipeListRole.FAVORITES)).thenReturn(RecipeListRole.FAVORITES);
        when(recipeListService.moveRecipe("r-fails", RecipeListRole.REJECTED))
                .thenThrow(new RuntimeException("Cookidoo indisponible"));

        Map<String, RecipeListRole> votes = new LinkedHashMap<>();
        votes.put("r-ok", RecipeListRole.FAVORITES);
        votes.put("r-fails", RecipeListRole.REJECTED);

        WeeklyReviewService.SubmitResult result = weeklyReviewService.submitVotes(votes);

        assertThat(result.moved()).extracting(WeeklyReviewService.VoteOutcome::recipeId).containsExactly("r-ok");
        assertThat(result.failed()).extracting(WeeklyReviewService.VoteFailure::recipeId).containsExactly("r-fails");
        assertThat(result.failed().get(0).error()).isEqualTo("Cookidoo indisponible");
    }

    private MealPlanEntry entry(LocalDate date, MealType mealType, String recipeId, String recipeName) {
        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(date);
        entry.setMealType(mealType);
        entry.setRecipeIdSnapshot(recipeId);
        entry.setRecipeNameSnapshot(recipeName);
        return entry;
    }
}
