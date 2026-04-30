package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionAgent;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.generated.api.ChatApiController;
import fr.seblaporte.kitchenvault.generated.model.MealType;
import fr.seblaporte.kitchenvault.generated.model.PendingMealChangeDto;
import fr.seblaporte.kitchenvault.generated.model.QuickActionDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyPlanChatResponse;
import fr.seblaporte.kitchenvault.mapper.RecipeMapper;
import fr.seblaporte.kitchenvault.service.RecipeService;
import fr.seblaporte.kitchenvault.service.WeeklyMealPlanService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ChatApiController.class})
@Import(ChatDelegate.class)
class ChatWeeklyMealPlanDelegateTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean WeeklyMealPlanService weeklyMealPlanService;
    @MockitoBean RecipeSuggestionAgent recipeSuggestionAgent;
    @MockitoBean PostgresChatMemoryStore chatMemoryStore;
    @MockitoBean RecipeService recipeService;
    @MockitoBean RecipeMapper recipeMapper;

    @Test
    void chatMealPlanWeek_validRequest_returns200WithReply() throws Exception {
        WeeklyPlanChatResponse response = new WeeklyPlanChatResponse()
                .reply("Voici votre menu pour la semaine !")
                .quickActions(List.of(new QuickActionDto().label("Je valide le menu ✓").action("CONFIRM")));

        when(weeklyMealPlanService.processChat(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/chat/meal-plan-week")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-1",
                                  "weekStart": "2026-05-04",
                                  "message": "Planifie ma semaine"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Voici votre menu pour la semaine !"))
                .andExpect(jsonPath("$.quickActions[0].label").value("Je valide le menu ✓"));
    }

    @Test
    void chatMealPlanWeek_emptyRecipeBase_returns422() throws Exception {
        when(weeklyMealPlanService.processChat(any()))
                .thenThrow(new WeeklyMealPlanService.EmptyRecipeBaseException("Base vide"));

        mockMvc.perform(post("/api/v1/chat/meal-plan-week")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-1",
                                  "weekStart": "2026-05-04",
                                  "message": "Planifie ma semaine"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void chatMealPlanWeek_agentError_returns503() throws Exception {
        when(weeklyMealPlanService.processChat(any()))
                .thenThrow(new RuntimeException("AI service unavailable"));

        mockMvc.perform(post("/api/v1/chat/meal-plan-week")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-1",
                                  "weekStart": "2026-05-04",
                                  "message": "Planifie ma semaine"
                                }
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void chatMealPlanWeek_withPendingChanges_returnsThem() throws Exception {
        PendingMealChangeDto change = new PendingMealChangeDto()
                .date(LocalDate.of(2026, 5, 5))
                .mealType(MealType.DINNER)
                .recipeId("r1")
                .recipeName("Risotto")
                .previousRecipeName("Carbonara");

        WeeklyPlanChatResponse response = new WeeklyPlanChatResponse()
                .reply("Voulez-vous appliquer ce changement ?")
                .proposedChanges(List.of(change));

        when(weeklyMealPlanService.processChat(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/chat/meal-plan-week")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-1",
                                  "weekStart": "2026-05-04",
                                  "message": "Change le dîner de lundi"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedChanges[0].recipeName").value("Risotto"))
                .andExpect(jsonPath("$.proposedChanges[0].previousRecipeName").value("Carbonara"));
    }

    @Test
    void chatMealPlanWeek_withConstraints_passes() throws Exception {
        when(weeklyMealPlanService.processChat(any()))
                .thenReturn(new WeeklyPlanChatResponse().reply("Ok"));

        mockMvc.perform(post("/api/v1/chat/meal-plan-week")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-1",
                                  "weekStart": "2026-05-04",
                                  "message": "Génère mon menu",
                                  "constraints": {
                                    "absenceDays": ["WEDNESDAY"],
                                    "priorityIngredients": "crevettes dans 2 jours",
                                    "thermalConstraintDays": ["THURSDAY", "FRIDAY"],
                                    "freeText": "Pas de plats trop lourds"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Ok"));
    }
}
