package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCalendarDay;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCalendarDayRecipe;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.repository.MealPlanEntryRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class CookidooCalendarPullService {

    private static final Logger log = LoggerFactory.getLogger(CookidooCalendarPullService.class);

    private final CookidooServiceClient cookidooServiceClient;
    private final MealPlanService mealPlanService;
    private final MealPlanEntryRepository mealPlanEntryRepository;
    private final RecipeRepository recipeRepository;
    private final SyncService syncService;

    public CookidooCalendarPullService(
            CookidooServiceClient cookidooServiceClient,
            MealPlanService mealPlanService,
            MealPlanEntryRepository mealPlanEntryRepository,
            RecipeRepository recipeRepository,
            SyncService syncService
    ) {
        this.cookidooServiceClient = cookidooServiceClient;
        this.mealPlanService = mealPlanService;
        this.mealPlanEntryRepository = mealPlanEntryRepository;
        this.recipeRepository = recipeRepository;
        this.syncService = syncService;
    }

    @Transactional
    public void pullWeek(LocalDate weekStart) {
        List<CookidooCalendarDay> days =
                cookidooServiceClient.getCalendarWeek(weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE));

        for (CookidooCalendarDay day : days) {
            LocalDate date = LocalDate.parse(day.id());
            for (CookidooCalendarDayRecipe recipe : day.recipes()) {
                try {
                    importRecipe(date, recipe);
                } catch (Exception e) {
                    log.warn("Failed to import Cookidoo recipe {} for {}: {}", recipe.id(), date, e.getMessage());
                }
            }
        }
    }

    private void importRecipe(LocalDate date, CookidooCalendarDayRecipe recipe) {
        boolean alreadyImported = mealPlanEntryRepository
                .existsByEntryDateAndMealTypeAndRecipeIdSnapshot(date, MealType.UNDEFINED, recipe.id());
        if (alreadyImported) {
            return;
        }

        if (recipeRepository.findById(recipe.id()).isEmpty()) {
            syncService.upsertRecipe(cookidooServiceClient.getRecipeById(recipe.id()));
        }

        mealPlanService.addUndefinedEntry(date, recipe.id());
    }
}
