package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.generated.model.MealPlanBulkEntryDto;
import fr.seblaporte.kitchenvault.repository.MealPlanEntryRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class MealPlanService {

    private final MealPlanEntryRepository mealPlanEntryRepository;
    private final RecipeRepository recipeRepository;

    public MealPlanService(MealPlanEntryRepository mealPlanEntryRepository, RecipeRepository recipeRepository) {
        this.mealPlanEntryRepository = mealPlanEntryRepository;
        this.recipeRepository = recipeRepository;
    }

    public List<MealPlanEntry> getWeekPlan(LocalDate weekStart) {
        return mealPlanEntryRepository.findWeekPlan(weekStart, weekStart.plusDays(6));
    }

    @Transactional
    public MealPlanEntry upsertEntry(LocalDate date, MealType mealType, String recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));

        MealPlanEntry entry = mealPlanEntryRepository.findByEntryDateAndMealType(date, mealType)
                .orElseGet(MealPlanEntry::new);

        entry.setEntryDate(date);
        entry.setMealType(mealType);
        entry.setRecipe(recipe);
        entry.setRecipeNameSnapshot(recipe.getName());
        entry.setRecipeIdSnapshot(recipe.getId());

        return mealPlanEntryRepository.save(entry);
    }

    @Transactional
    public List<MealPlanEntry> upsertBulk(List<MealPlanBulkEntryDto> entries) {
        return entries.stream()
                .map(e -> upsertEntry(
                        e.getDate(),
                        MealType.valueOf(e.getMealType().getValue()),
                        e.getRecipeId()))
                .toList();
    }

    @Transactional
    public void removeEntry(LocalDate date, MealType mealType) {
        mealPlanEntryRepository.findByEntryDateAndMealType(date, mealType)
                .ifPresent(mealPlanEntryRepository::delete);
    }

    public List<MealPlanEntry> getRecipeHistory(String recipeId, int limit) {
        return mealPlanEntryRepository.findByRecipeIdOrderByEntryDateDesc(recipeId, PageRequest.of(0, limit));
    }

    public List<Recipe> suggest(LocalDate date, MealType mealType, Integer maxTotalMinutes, int count) {
        LocalDate from = LocalDate.now().minusDays(28);
        List<String> recentIds = mealPlanEntryRepository.findRecentRecipeIds(from, LocalDate.now());
        if (recentIds.isEmpty()) {
            return recipeRepository.findRandomRecipes(maxTotalMinutes, count);
        }
        return recipeRepository.findRandomRecipesExcluding(recentIds, maxTotalMinutes, count);
    }
}
