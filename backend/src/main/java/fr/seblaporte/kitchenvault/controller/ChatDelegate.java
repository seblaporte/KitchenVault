package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionAgent;
import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionResult;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.generated.api.ChatApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.ChatMessageDto;
import fr.seblaporte.kitchenvault.generated.model.ChatResponseDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeSummaryDto;
import fr.seblaporte.kitchenvault.generated.model.WeeklyPlanChatRequest;
import fr.seblaporte.kitchenvault.generated.model.WeeklyPlanChatResponse;
import fr.seblaporte.kitchenvault.mapper.RecipeMapper;
import fr.seblaporte.kitchenvault.service.RecipeService;
import fr.seblaporte.kitchenvault.service.WeeklyMealPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChatDelegate implements ChatApiDelegate {

    private static final Logger log = LoggerFactory.getLogger(ChatDelegate.class);

    private final RecipeSuggestionAgent recipeSuggestionAgent;
    private final PostgresChatMemoryStore chatMemoryStore;
    private final RecipeService recipeService;
    private final RecipeMapper recipeMapper;
    private final WeeklyMealPlanService weeklyMealPlanService;

    private String lastSessionId = null;

    public ChatDelegate(RecipeSuggestionAgent recipeSuggestionAgent,
                        PostgresChatMemoryStore chatMemoryStore,
                        RecipeService recipeService,
                        RecipeMapper recipeMapper,
                        WeeklyMealPlanService weeklyMealPlanService) {
        this.recipeSuggestionAgent = recipeSuggestionAgent;
        this.chatMemoryStore = chatMemoryStore;
        this.recipeService = recipeService;
        this.recipeMapper = recipeMapper;
        this.weeklyMealPlanService = weeklyMealPlanService;
    }

    @Override
    public ResponseEntity<WeeklyPlanChatResponse> chatMealPlanWeek(WeeklyPlanChatRequest request) {
        try {
            WeeklyPlanChatResponse response = weeklyMealPlanService.processChat(request);
            return ResponseEntity.ok(response);
        } catch (WeeklyMealPlanService.EmptyRecipeBaseException e) {
            log.warn("Empty recipe base for session {}: {}", request.getSessionId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception e) {
            log.error("Weekly meal plan error for session {}: {}", request.getSessionId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @Override
    public ResponseEntity<ChatResponseDto> chatRecipe(ChatMessageDto chatMessageDto) {
        String sessionId = chatMessageDto.getSessionId();
        if (!sessionId.equals(lastSessionId)) {
            chatMemoryStore.deleteAllMessages();
            lastSessionId = sessionId;
        }
        try {
            RecipeSuggestionResult result = recipeSuggestionAgent.suggestRecipes(sessionId, chatMessageDto.getMessage());
            List<RecipeSummaryDto> suggestions = result.recipeIds() == null ? List.of() :
                    result.recipeIds().stream()
                            .map(recipeService::getRecipeById)
                            .flatMap(Optional::stream)
                            .map(recipeMapper::toSummaryDto)
                            .toList();
            return ResponseEntity.ok(new ChatResponseDto()
                    .reply(result.reply() != null ? result.reply().replace("\\n", "\n") : "")
                    .suggestions(suggestions));
        } catch (Exception e) {
            log.error("Recipe agent error for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

}
