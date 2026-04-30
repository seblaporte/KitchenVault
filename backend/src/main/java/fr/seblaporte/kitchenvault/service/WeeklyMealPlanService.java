package fr.seblaporte.kitchenvault.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyMealPlanAgent;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyPlanAgentResult;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyPlanAgentResult.AgentAction;
import fr.seblaporte.kitchenvault.ai.agent.WeeklyPlanAgentResult.MealSlotAssignment;
import fr.seblaporte.kitchenvault.entity.MealPlanEntry;
import fr.seblaporte.kitchenvault.entity.MealType;
import fr.seblaporte.kitchenvault.entity.WeeklyPlanSession;
import fr.seblaporte.kitchenvault.generated.model.PendingMealChangeDto;
import fr.seblaporte.kitchenvault.generated.model.QuickActionDto;
import fr.seblaporte.kitchenvault.generated.model.WeekConstraintsDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyPlanChatRequest;
import fr.seblaporte.kitchenvault.generated.model.WeeklyPlanChatResponse;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import fr.seblaporte.kitchenvault.repository.WeeklyPlanSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeeklyMealPlanService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyMealPlanService.class);

    private final WeeklyMealPlanAgent agent;
    private final WeeklyPlanSessionRepository sessionRepository;
    private final MealPlanService mealPlanService;
    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeeklyMealPlanService(WeeklyMealPlanAgent agent,
                                 WeeklyPlanSessionRepository sessionRepository,
                                 MealPlanService mealPlanService,
                                 RecipeRepository recipeRepository) {
        this.agent = agent;
        this.sessionRepository = sessionRepository;
        this.mealPlanService = mealPlanService;
        this.recipeRepository = recipeRepository;
    }

    @Transactional
    public WeeklyPlanChatResponse processChat(WeeklyPlanChatRequest request) {
        if (recipeRepository.count() == 0) {
            throw new EmptyRecipeBaseException(
                    "Vous n'avez pas encore de recettes dans votre base. Ajoutez des recettes pour utiliser la planification IA.");
        }

        WeeklyPlanSession session = sessionRepository.findById(request.getSessionId())
                .orElseGet(() -> sessionRepository.save(
                        new WeeklyPlanSession(request.getSessionId(), request.getWeekStart())));
        session.setLastActiveAt(Instant.now());
        sessionRepository.save(session);

        List<MealPlanEntry> currentPlan = mealPlanService.getWeekPlan(request.getWeekStart());
        String enrichedMessage = buildEnrichedMessage(request, session, currentPlan);

        WeeklyPlanAgentResult result;
        try {
            result = agent.chat(request.getSessionId(), enrichedMessage);
        } catch (Exception e) {
            log.error("Weekly meal plan agent error for session {}: {}", request.getSessionId(), e.getMessage(), e);
            throw e;
        }

        // Reload session after agent call (memory store may have updated it externally)
        session = sessionRepository.findById(request.getSessionId()).orElseThrow();

        // Process action on pending changes
        if (result.action() == AgentAction.APPLY_PENDING) {
            applyPendingChanges(session);
            session = sessionRepository.findById(request.getSessionId()).orElseThrow();
        } else if (result.action() == AgentAction.CLEAR_PENDING) {
            session.setPendingChanges(null);
            session.setLastActiveAt(Instant.now());
            sessionRepository.save(session);
        }

        // Process meal assignments
        List<MealSlotAssignment> assignments = result.mealAssignments() != null
                ? result.mealAssignments() : List.of();

        if (!assignments.isEmpty()) {
            if (!session.isInitialDone()) {
                assignments.forEach(a -> {
                    try {
                        mealPlanService.upsertEntry(
                                LocalDate.parse(a.date()),
                                MealType.valueOf(a.mealType()),
                                a.recipeId());
                    } catch (Exception e) {
                        log.warn("Failed to apply meal assignment {}/{}/{}: {}", a.date(), a.mealType(), a.recipeId(), e.getMessage());
                    }
                });
                session.setInitialDone(true);
                session.setLastActiveAt(Instant.now());
                sessionRepository.save(session);
            } else {
                storePendingChanges(session, assignments);
                session = sessionRepository.findById(request.getSessionId()).orElseThrow();
            }
        }

        // Build response
        List<Map<String, Object>> rawPending = deserializePending(session.getPendingChanges());
        List<PendingMealChangeDto> pendingDtos = rawPending.stream()
                .map(this::toPendingMealChangeDto)
                .toList();

        List<QuickActionDto> quickActionDtos = result.quickActions() == null ? List.of() :
                result.quickActions().stream()
                        .map(qa -> new QuickActionDto().label(qa.label()).action(qa.action()))
                        .toList();

        String reply = result.reply() != null ? result.reply().replace("\\n", "\n") : "";

        return new WeeklyPlanChatResponse()
                .reply(reply)
                .proposedChanges(pendingDtos.isEmpty() ? null : pendingDtos)
                .quickActions(quickActionDtos.isEmpty() ? null : quickActionDtos);
    }

    private String buildEnrichedMessage(WeeklyPlanChatRequest request, WeeklyPlanSession session,
                                        List<MealPlanEntry> currentPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Contexte de session]\n");
        sb.append("sessionId: ").append(request.getSessionId()).append("\n");
        sb.append("weekStart: ").append(request.getWeekStart()).append("\n");
        sb.append("initialDone: ").append(session.isInitialDone()).append("\n\n");

        sb.append("[Planning courant — semaine du ").append(request.getWeekStart()).append("]\n");
        for (int i = 0; i < 7; i++) {
            LocalDate date = request.getWeekStart().plusDays(i);
            String lunch = findSlot(currentPlan, date, MealType.LUNCH);
            String dinner = findSlot(currentPlan, date, MealType.DINNER);
            sb.append(date.getDayOfWeek()).append(" ").append(date)
                    .append(" | Déjeuner: ").append(lunch)
                    .append(" | Dîner: ").append(dinner).append("\n");
        }
        sb.append("\n");

        WeekConstraintsDto constraints = request.getConstraints();
        if (constraints != null) {
            sb.append("[Contraintes initiales]\n");
            if (constraints.getAbsenceDays() != null && !constraints.getAbsenceDays().isEmpty()) {
                sb.append("Jours d'absence : ").append(String.join(", ", constraints.getAbsenceDays())).append("\n");
            }
            if (constraints.getPriorityIngredients() != null && !constraints.getPriorityIngredients().isBlank()) {
                sb.append("Ingrédients prioritaires : ").append(constraints.getPriorityIngredients()).append("\n");
            }
            if (constraints.getThermalConstraintDays() != null && !constraints.getThermalConstraintDays().isEmpty()) {
                sb.append("Jours sans four : ").append(String.join(", ", constraints.getThermalConstraintDays())).append("\n");
            }
            if (constraints.getFreeText() != null && !constraints.getFreeText().isBlank()) {
                sb.append("Remarques : ").append(constraints.getFreeText()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("[Message utilisateur]\n").append(request.getMessage());
        return sb.toString();
    }

    private String findSlot(List<MealPlanEntry> plan, LocalDate date, MealType mealType) {
        return plan.stream()
                .filter(e -> e.getEntryDate().equals(date) && e.getMealType() == mealType)
                .findFirst()
                .map(e -> e.getRecipeNameSnapshot() + " (id: " + e.getRecipeIdSnapshot() + ")")
                .orElse("(vide)");
    }

    private void applyPendingChanges(WeeklyPlanSession session) {
        List<Map<String, Object>> pending = deserializePending(session.getPendingChanges());
        for (Map<String, Object> change : pending) {
            try {
                mealPlanService.upsertEntry(
                        LocalDate.parse((String) change.get("date")),
                        MealType.valueOf((String) change.get("mealType")),
                        (String) change.get("recipeId"));
            } catch (Exception e) {
                log.warn("Failed to apply pending change {}: {}", change, e.getMessage());
            }
        }
        session.setPendingChanges(null);
        session.setLastActiveAt(Instant.now());
        sessionRepository.save(session);
    }

    private void storePendingChanges(WeeklyPlanSession session, List<MealSlotAssignment> assignments) {
        List<Map<String, Object>> pending = new ArrayList<>();
        for (MealSlotAssignment a : assignments) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("date", a.date());
            change.put("mealType", a.mealType());
            change.put("recipeId", a.recipeId());
            change.put("recipeName", a.recipeName());
            // previousRecipeName not available from LLM assignment — omit
            pending.add(change);
        }
        try {
            session.setPendingChanges(objectMapper.writeValueAsString(pending));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize pending changes", e);
        }
        session.setLastActiveAt(Instant.now());
        sessionRepository.save(session);
    }

    List<Map<String, Object>> deserializePending(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize pending changes", e);
            return new ArrayList<>();
        }
    }

    private PendingMealChangeDto toPendingMealChangeDto(Map<String, Object> raw) {
        PendingMealChangeDto dto = new PendingMealChangeDto();
        dto.setDate(java.time.LocalDate.parse((String) raw.get("date")));
        dto.setMealType(fr.seblaporte.kitchenvault.generated.model.MealType.valueOf((String) raw.get("mealType")));
        dto.setRecipeId((String) raw.get("recipeId"));
        dto.setRecipeName((String) raw.get("recipeName"));
        dto.setPreviousRecipeName((String) raw.get("previousRecipeName"));
        return dto;
    }

    public static class EmptyRecipeBaseException extends RuntimeException {
        public EmptyRecipeBaseException(String message) {
            super(message);
        }
    }
}
