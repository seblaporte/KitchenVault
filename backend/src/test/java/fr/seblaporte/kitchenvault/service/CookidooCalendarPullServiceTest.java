package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.cookidoo.CookidooServiceClient;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCalendarDay;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooCalendarDayRecipe;
import fr.seblaporte.kitchenvault.cookidoo.model.CookidooRecipeDetails;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.repository.MealPlanEntryRepository;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CookidooCalendarPullServiceTest {

    @Mock
    CookidooServiceClient cookidooServiceClient;

    @Mock
    MealPlanService mealPlanService;

    @Mock
    MealPlanEntryRepository mealPlanEntryRepository;

    @Mock
    RecipeRepository recipeRepository;

    @Mock
    SyncService syncService;

    @InjectMocks
    CookidooCalendarPullService service;

    private static final LocalDate MONDAY = LocalDate.of(2025, 5, 19);
    private static final List<MealType> ANY_MEAL_TYPE = List.of(MealType.UNDEFINED, MealType.LUNCH, MealType.DINNER);

    private CookidooCalendarDayRecipe cookidooRecipe(String id) {
        return new CookidooCalendarDayRecipe(id, "Recipe " + id, 1800, "thumb", "img", "https://cookidoo.example/" + id);
    }

    private CookidooRecipeDetails recipeDetails(String id) {
        return new CookidooRecipeDetails(id, "Recipe " + id, "thumb", "img", "https://cookidoo.example/" + id,
                "EASY", 4, 900, 1800, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void pullWeek_withNewRecipe_fetchesDetailsAndAddsUndefinedEntry() {
        CookidooCalendarDayRecipe recipe = cookidooRecipe("r-1");
        CookidooCalendarDay day = new CookidooCalendarDay("2025-05-19", "Day", List.of(recipe));
        when(cookidooServiceClient.getCalendarWeek("2025-05-19")).thenReturn(List.of(day));
        when(mealPlanEntryRepository.existsByEntryDateAndMealTypeInAndRecipeIdSnapshot(MONDAY, ANY_MEAL_TYPE, "r-1"))
                .thenReturn(false);
        when(recipeRepository.findById("r-1")).thenReturn(Optional.empty());
        CookidooRecipeDetails details = recipeDetails("r-1");
        when(cookidooServiceClient.getRecipeById("r-1")).thenReturn(details);

        service.pullWeek(MONDAY);

        verify(syncService).upsertRecipe(details);
        verify(mealPlanService).addUndefinedEntry(MONDAY, "r-1");
    }

    @Test
    void pullWeek_recipeAlreadyLocal_skipsFetch() {
        CookidooCalendarDayRecipe recipe = cookidooRecipe("r-1");
        CookidooCalendarDay day = new CookidooCalendarDay("2025-05-19", "Day", List.of(recipe));
        when(cookidooServiceClient.getCalendarWeek("2025-05-19")).thenReturn(List.of(day));
        when(mealPlanEntryRepository.existsByEntryDateAndMealTypeInAndRecipeIdSnapshot(MONDAY, ANY_MEAL_TYPE, "r-1"))
                .thenReturn(false);
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(mock(Recipe.class)));

        service.pullWeek(MONDAY);

        verify(cookidooServiceClient, never()).getRecipeById(any());
        verify(syncService, never()).upsertRecipe(any());
        verify(mealPlanService).addUndefinedEntry(MONDAY, "r-1");
    }

    @Test
    void pullWeek_recipeAlreadyPlanned_skipsEntirely() {
        CookidooCalendarDayRecipe recipe = cookidooRecipe("r-1");
        CookidooCalendarDay day = new CookidooCalendarDay("2025-05-19", "Day", List.of(recipe));
        when(cookidooServiceClient.getCalendarWeek("2025-05-19")).thenReturn(List.of(day));
        when(mealPlanEntryRepository.existsByEntryDateAndMealTypeInAndRecipeIdSnapshot(MONDAY, ANY_MEAL_TYPE, "r-1"))
                .thenReturn(true);

        service.pullWeek(MONDAY);

        verifyNoInteractions(recipeRepository, syncService);
        verify(cookidooServiceClient, never()).getRecipeById(any());
        verify(mealPlanService, never()).addUndefinedEntry(any(), any());
    }

    @Test
    void pullWeek_emptyWeek_noEntriesAdded() {
        when(cookidooServiceClient.getCalendarWeek("2025-05-19")).thenReturn(List.of());

        service.pullWeek(MONDAY);

        verifyNoInteractions(mealPlanService, mealPlanEntryRepository, recipeRepository, syncService);
    }

    @Test
    void pullWeek_singleRecipeFails_continuesWithOthers() {
        CookidooCalendarDayRecipe failing = cookidooRecipe("r-1");
        CookidooCalendarDayRecipe ok = cookidooRecipe("r-2");
        CookidooCalendarDay day = new CookidooCalendarDay("2025-05-19", "Day", List.of(failing, ok));
        when(cookidooServiceClient.getCalendarWeek("2025-05-19")).thenReturn(List.of(day));
        when(mealPlanEntryRepository.existsByEntryDateAndMealTypeInAndRecipeIdSnapshot(eq(MONDAY), eq(ANY_MEAL_TYPE), any()))
                .thenReturn(false);
        when(recipeRepository.findById("r-1")).thenReturn(Optional.of(mock(Recipe.class)));
        when(recipeRepository.findById("r-2")).thenReturn(Optional.of(mock(Recipe.class)));
        doThrow(new RuntimeException("boom")).when(mealPlanService).addUndefinedEntry(MONDAY, "r-1");

        service.pullWeek(MONDAY);

        verify(mealPlanService).addUndefinedEntry(MONDAY, "r-1");
        verify(mealPlanService).addUndefinedEntry(MONDAY, "r-2");
    }
}
