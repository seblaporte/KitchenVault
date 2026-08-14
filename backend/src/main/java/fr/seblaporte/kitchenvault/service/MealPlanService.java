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
    public MealPlanEntry addUndefinedEntry(LocalDate date, String recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));

        boolean alreadyPlanned = mealPlanEntryRepository.existsByEntryDateAndMealTypeInAndRecipeIdSnapshot(
                date, List.of(MealType.UNDEFINED, MealType.LUNCH, MealType.DINNER), recipeId);
        if (alreadyPlanned) {
            throw new SlotOccupiedException("Cette recette est déjà planifiée ce jour-là");
        }

        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(date);
        entry.setMealType(MealType.UNDEFINED);
        entry.setRecipe(recipe);
        entry.setRecipeNameSnapshot(recipe.getName());
        entry.setRecipeIdSnapshot(recipe.getId());

        return mealPlanEntryRepository.save(entry);
    }

    @Transactional
    public void removeUndefinedEntryById(Long id) {
        mealPlanEntryRepository.findById(id)
                .filter(entry -> entry.getMealType() == MealType.UNDEFINED)
                .ifPresent(mealPlanEntryRepository::delete);
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

    @Transactional
    public MealPlanEntry relocateEntry(LocalDate sourceDate, MealType sourceMealType,
                                        LocalDate targetDate, MealType targetMealType) {
        MealPlanEntry entry = mealPlanEntryRepository.findByEntryDateAndMealType(sourceDate, sourceMealType)
                .orElseThrow(() -> new NoSuchElementException("Aucune recette au créneau source"));

        if (sourceDate.equals(targetDate) && sourceMealType == targetMealType) {
            return entry;
        }

        mealPlanEntryRepository.findByEntryDateAndMealType(targetDate, targetMealType)
                .ifPresent(existing -> { throw new SlotOccupiedException("Le créneau cible est déjà occupé"); });

        entry.setEntryDate(targetDate);
        entry.setMealType(targetMealType);
        return mealPlanEntryRepository.save(entry);
    }

    @Transactional
    public MealPlanEntry relocateUndefinedEntry(Long sourceId, LocalDate targetDate, MealType targetMealType) {
        MealPlanEntry source = mealPlanEntryRepository.findById(sourceId)
                .filter(entry -> entry.getMealType() == MealType.UNDEFINED)
                .orElseThrow(() -> new NoSuchElementException("Aucune recette « Non défini » avec cet identifiant"));

        LocalDate sourceDate = source.getEntryDate();

        mealPlanEntryRepository.findByEntryDateAndMealType(targetDate, targetMealType)
                .ifPresent(existing -> {
                    existing.setEntryDate(sourceDate);
                    existing.setMealType(MealType.UNDEFINED);
                    mealPlanEntryRepository.saveAndFlush(existing);
                });

        source.setEntryDate(targetDate);
        source.setMealType(targetMealType);
        return mealPlanEntryRepository.save(source);
    }

    public static class SlotOccupiedException extends RuntimeException {
        public SlotOccupiedException(String message) {
            super(message);
        }
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
