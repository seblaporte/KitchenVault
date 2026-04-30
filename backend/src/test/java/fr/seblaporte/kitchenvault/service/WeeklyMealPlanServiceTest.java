package fr.seblaporte.kitchenvault.service;

import fr.seblaporte.kitchenvault.ai.agent.WeeklyMealPlanAgent;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyPlanAgentResult;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyPlanAgentResult.AgentAction;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyPlanAgentResult.MealSlotAssignment;
import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.WeeklyPlanSession;
import fr.seblaporte.kitchenvault.generated.model.WeeklyPlanChatRequest;
import fr.seblaporte.kitchenvault.generated.model.WeeklyPlanChatResponse;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.WeeklyPlanSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyMealPlanServiceTest {

    @Mock WeeklyMealPlanAgent agent;
    @Mock WeeklyPlanSessionRepository sessionRepository;
    @Mock MealPlanService mealPlanService;
    @Mock RecipeRepository recipeRepository;

    @InjectMocks WeeklyMealPlanService service;

    private static final LocalDate WEEK_START = LocalDate.of(2026, 5, 4);

    @Test
    void processChat_emptyRecipeBase_throwsEmptyRecipeBaseException() {
        when(recipeRepository.count()).thenReturn(0L);

        WeeklyPlanChatRequest request = buildRequest("Planifie ma semaine");
        assertThatThrownBy(() -> service.processChat(request))
                .isInstanceOf(WeeklyMealPlanService.EmptyRecipeBaseException.class);

        verifyNoInteractions(agent);
    }

    @Test
    void processChat_newSession_createsSessionAndCallsAgent() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession newSession = new WeeklyPlanSession("session-1", WEEK_START);
        when(sessionRepository.findById("session-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(newSession));
        when(sessionRepository.save(any())).thenReturn(newSession);
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of());
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Voici votre menu !", List.of(), List.of(), null));

        WeeklyPlanChatResponse response = service.processChat(buildRequest("Planifie ma semaine"));

        assertThat(response.getReply()).isEqualTo("Voici votre menu !");
        assertThat(response.getProposedChanges()).isNull();
        verify(agent).chat(eq("session-1"), anyString());
    }

    @Test
    void processChat_existingSession_resumesConversation() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession session = new WeeklyPlanSession("session-1", WEEK_START);
        session.setInitialDone(true);
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of());
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Je propose ce changement.", List.of(), List.of(), null));

        WeeklyPlanChatResponse response = service.processChat(buildRequest("Change le dîner de lundi"));

        assertThat(response.getReply()).isEqualTo("Je propose ce changement.");
    }

    @Test
    void processChat_initialGeneration_appliesAssignmentsDirectly() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession session = new WeeklyPlanSession("session-1", WEEK_START);
        when(sessionRepository.findById("session-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of());

        List<MealSlotAssignment> assignments = List.of(
                new MealSlotAssignment("2026-05-05", "DINNER", "r1", "Risotto"));
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Menu généré !", List.of(), assignments, null));

        service.processChat(buildRequest("Planifie ma semaine"));

        verify(mealPlanService).upsertEntry(LocalDate.of(2026, 5, 5), MealType.DINNER, "r1");
        assertThat(session.isInitialDone()).isTrue();
    }

    @Test
    void processChat_modificationWhenInitialDone_storesAsPending() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession session = new WeeklyPlanSession("session-1", WEEK_START);
        session.setInitialDone(true);
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of());

        List<MealSlotAssignment> assignments = List.of(
                new MealSlotAssignment("2026-05-05", "DINNER", "r1", "Risotto"));
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Confirmation ?", List.of(), assignments, null));

        WeeklyPlanChatResponse response = service.processChat(buildRequest("Change le dîner de mardi"));

        verify(mealPlanService, never()).upsertEntry(any(), any(), any());
        assertThat(response.getProposedChanges()).hasSize(1);
        assertThat(response.getProposedChanges().get(0).getRecipeName()).isEqualTo("Risotto");
    }

    @Test
    void processChat_applyPendingAction_appliesPendingChanges() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession session = new WeeklyPlanSession("session-1", WEEK_START);
        session.setInitialDone(true);
        session.setPendingChanges("[{\"date\":\"2026-05-05\",\"mealType\":\"DINNER\",\"recipeId\":\"r1\",\"recipeName\":\"Risotto\"}]");
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of());
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Modifications appliquées !", List.of(), List.of(), AgentAction.APPLY_PENDING));

        service.processChat(buildRequest("CONFIRM"));

        verify(mealPlanService).upsertEntry(LocalDate.of(2026, 5, 5), MealType.DINNER, "r1");
        assertThat(session.getPendingChanges()).isNull();
    }

    @Test
    void processChat_clearPendingAction_clearsPendingChanges() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession session = new WeeklyPlanSession("session-1", WEEK_START);
        session.setInitialDone(true);
        session.setPendingChanges("[{\"date\":\"2026-05-05\",\"mealType\":\"DINNER\",\"recipeId\":\"r1\",\"recipeName\":\"Risotto\"}]");
        when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of());
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Modifications annulées.", List.of(), List.of(), AgentAction.CLEAR_PENDING));

        service.processChat(buildRequest("REJECT"));

        verify(mealPlanService, never()).upsertEntry(any(), any(), any());
        assertThat(session.getPendingChanges()).isNull();
    }

    @Test
    void processChat_enrichedMessageContainsSessionContext() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession session = new WeeklyPlanSession("session-1", WEEK_START);
        when(sessionRepository.findById("session-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of());
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Ok", List.of(), List.of(), null));

        service.processChat(buildRequest("Planifie"));

        verify(agent).chat(eq("session-1"), argThat(msg ->
                msg.contains("sessionId: session-1") &&
                msg.contains("weekStart: 2026-05-04") &&
                msg.contains("initialDone: false")));
    }

    @Test
    void processChat_enrichedMessageContainsCurrentPlan() {
        when(recipeRepository.count()).thenReturn(5L);
        WeeklyPlanSession session = new WeeklyPlanSession("session-1", WEEK_START);
        when(sessionRepository.findById("session-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        MealPlanEntry entry = new MealPlanEntry();
        entry.setEntryDate(WEEK_START);
        entry.setMealType(MealType.LUNCH);
        entry.setRecipeNameSnapshot("Salade niçoise");
        entry.setRecipeIdSnapshot("abc123");
        when(mealPlanService.getWeekPlan(WEEK_START)).thenReturn(List.of(entry));
        when(agent.chat(anyString(), anyString()))
                .thenReturn(new WeeklyPlanAgentResult("Ok", List.of(), List.of(), null));

        service.processChat(buildRequest("Planifie"));

        verify(agent).chat(eq("session-1"), argThat(msg ->
                msg.contains("Salade niçoise") && msg.contains("abc123")));
    }

    private WeeklyPlanChatRequest buildRequest(String message) {
        return new WeeklyPlanChatRequest()
                .sessionId("session-1")
                .weekStart(WEEK_START)
                .message(message);
    }
}
