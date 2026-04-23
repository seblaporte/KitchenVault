package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.generated.api.MenuPlanApiController;
import fr.seblaporte.kitchenvault.generated.model.MealPlanEntryDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeHistoryDto;
import fr.seblaporte.kitchenvault.mapper.MealPlanMapper;
import fr.seblaporte.kitchenvault.service.MealPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {MenuPlanApiController.class})
@Import(MenuPlanDelegate.class)
class MenuPlanDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean MealPlanService mealPlanService;
    @MockitoBean MealPlanMapper mealPlanMapper;

    @Test
    void getWeekPlan_withValidMonday_returnsOk() throws Exception {
        when(mealPlanService.getWeekPlan(LocalDate.of(2024, 4, 1))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/menu-plan").param("weekStart", "2024-04-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isArray());
    }

    @Test
    void getWeekPlan_withNonMonday_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/menu-plan").param("weekStart", "2024-04-02"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upsertEntry_withValidRecipe_returnsOk() throws Exception {
        Recipe recipe = new Recipe("r-1");
        recipe.setName("Tarte");
        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(LocalDate.of(2024, 4, 1));
        entry.setMealType(MealType.LUNCH);
        entry.setRecipe(recipe);
        entry.setRecipeNameSnapshot("Tarte");

        MealPlanEntryDto dto = new MealPlanEntryDto("Tarte");
        dto.setRecipeId("r-1");

        when(mealPlanService.upsertEntry(eq(LocalDate.of(2024, 4, 1)), eq(MealType.LUNCH), eq("r-1"))).thenReturn(entry);
        when(mealPlanMapper.toEntryDto(entry)).thenReturn(dto);

        mockMvc.perform(put("/api/v1/menu-plan/entries/2024-04-01/LUNCH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":\"r-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeName").value("Tarte"));
    }

    @Test
    void removeEntry_always_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/menu-plan/entries/2024-04-01/LUNCH"))
                .andExpect(status().isNoContent());

        verify(mealPlanService).removeEntry(LocalDate.of(2024, 4, 1), MealType.LUNCH);
    }

    @Test
    void getSuggestions_returnsArray() throws Exception {
        Recipe recipe = new Recipe("r-1");
        recipe.setName("Tarte");
        MealPlanEntryDto dto = new MealPlanEntryDto("Tarte");

        when(mealPlanService.suggest(any(), eq(MealType.LUNCH), eq(null), eq(3))).thenReturn(List.of(recipe));
        when(mealPlanMapper.toEntryDtoFromRecipe(recipe)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/menu-plan/suggestions")
                        .param("date", "2024-04-01")
                        .param("mealType", "LUNCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].recipeName").value("Tarte"));
    }

    @Test
    void getRecipeHistory_returnsHistory() throws Exception {
        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(LocalDate.of(2024, 3, 15));

        RecipeHistoryDto dto = new RecipeHistoryDto();
        dto.setRecipeId("r-1");
        dto.setDates(List.of(LocalDate.of(2024, 3, 15)));

        when(mealPlanService.getRecipeHistory("r-1", 52)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/menu-plan/history").param("recipeId", "r-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeId").value("r-1"))
                .andExpect(jsonPath("$.dates").isArray());
    }
}
