package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.RecipeListRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the weekly review (DISCOVERY-role recipes planned in a given week) and applies
 * the user's votes. {@link #submitVotes} deliberately has no transactional boundary of its
 * own: each {@link RecipeListService#moveRecipe} call runs in its own transaction, so a
 * failure on one recipe (e.g. Cookidoo unavailable) cannot roll back the others.
 */
@Service
public class WeeklyReviewService {

    public record Item(String recipeId, String recipeName, String thumbnailUrl, List<LocalDate> plannedDates) {}

    public record VoteOutcome(String recipeId, RecipeListRole newRole) {}

    public record VoteFailure(String recipeId, String error) {}

    public record SubmitResult(List<VoteOutcome> moved, List<VoteFailure> failed) {}

    private final MealPlanService mealPlanService;
    private final RecipeListService recipeListService;

    public WeeklyReviewService(MealPlanService mealPlanService, RecipeListService recipeListService) {
        this.mealPlanService = mealPlanService;
        this.recipeListService = recipeListService;
    }

    @Transactional(readOnly = true)
    public List<Item> generateReview(LocalDate weekStart) {
        List<MealPlanEntry> entries = mealPlanService.getWeekPlan(weekStart);

        Map<String, Item> byRecipe = new LinkedHashMap<>();
        for (MealPlanEntry entry : entries) {
            if (entry.getMealType() != MealType.LUNCH && entry.getMealType() != MealType.DINNER) {
                continue;
            }
            String recipeId = entry.getRecipeIdSnapshot();
            boolean isDiscovery = recipeListService.getRoleOfRecipe(recipeId)
                    .filter(role -> role == RecipeListRole.DISCOVERY)
                    .isPresent();
            if (!isDiscovery) {
                continue;
            }

            Item existing = byRecipe.get(recipeId);
            if (existing == null) {
                String thumbnailUrl = entry.getRecipe() != null ? entry.getRecipe().getThumbnailUrl() : null;
                List<LocalDate> dates = new ArrayList<>();
                dates.add(entry.getEntryDate());
                byRecipe.put(recipeId, new Item(recipeId, entry.getRecipeNameSnapshot(), thumbnailUrl, dates));
            } else {
                existing.plannedDates().add(entry.getEntryDate());
            }
        }

        return List.copyOf(byRecipe.values());
    }

    public SubmitResult submitVotes(Map<String, RecipeListRole> targetRoleByRecipeId) {
        List<VoteOutcome> moved = new ArrayList<>();
        List<VoteFailure> failed = new ArrayList<>();

        targetRoleByRecipeId.forEach((recipeId, targetRole) -> {
            try {
                RecipeListRole newRole = recipeListService.moveRecipe(recipeId, targetRole);
                moved.add(new VoteOutcome(recipeId, newRole));
            } catch (Exception e) {
                failed.add(new VoteFailure(recipeId, e.getMessage()));
            }
        });

        return new SubmitResult(moved, failed);
    }
}
