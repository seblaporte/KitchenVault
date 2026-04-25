package fr.seblaporte.kitchenvault.ai.tool;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.service.MealPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealPlanToolTest {

    @Mock MealPlanService mealPlanService;
    @Mock RecipeRepository recipeRepository;
    @InjectMocks MealPlanTool tool;

    @Test
    void planMeal_validInputs_callsUpsertEntry() {
        MealPlanEntry entry = mock(MealPlanEntry.class);
        when(entry.getRecipeNameSnapshot()).thenReturn("Poulet rôti");
        when(recipeRepository.existsById("recipe-1")).thenReturn(true);
        when(mealPlanService.upsertEntry(any(), any(), eq("recipe-1"))).thenReturn(entry);

        String result = tool.planMeal("2024-04-01", "MONDAY", "LUNCH", "recipe-1");

        assertThat(result).contains("Poulet rôti");
        verify(mealPlanService).upsertEntry(LocalDate.of(2024, 4, 1), MealType.LUNCH, "recipe-1");
    }

    @Test
    void planMeal_nonMonday_throwsIllegalArgument() {
        assertThatThrownBy(() -> tool.planMeal("2024-04-02", "TUESDAY", "LUNCH", "recipe-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lundi");
    }

    @Test
    void planMeal_invalidDateFormat_throwsIllegalArgument() {
        assertThatThrownBy(() -> tool.planMeal("01/04/2024", "MONDAY", "LUNCH", "recipe-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void planMeal_recipeNotFound_returnsErrorMessage() {
        when(recipeRepository.existsById("unknown")).thenReturn(false);

        String result = tool.planMeal("2024-04-01", "MONDAY", "DINNER", "unknown");

        assertThat(result).contains("introuvable");
        assertThat(result).contains("unknown");
        verifyNoInteractions(mealPlanService);
    }

    @Test
    void planMeal_invalidMealType_throwsIllegalArgument() {
        assertThatThrownBy(() -> tool.planMeal("2024-04-01", "MONDAY", "BREAKFAST", "recipe-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LUNCH ou DINNER");
    }

    @Test
    void getCurrentWeekPlan_noEntries_returnsEmptyMessage() {
        when(mealPlanService.getWeekPlan(LocalDate.of(2024, 4, 1))).thenReturn(java.util.List.of());

        String result = tool.getCurrentWeekPlan("2024-04-01");

        assertThat(result).contains("Aucun repas planifié");
    }
}
