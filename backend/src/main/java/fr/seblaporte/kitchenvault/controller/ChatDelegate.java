package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionAgent;
import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionResult;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.generated.api.ChatApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.ChatMessageDto;
import fr.seblaporte.kitchenvault.generated.model.ChatResponseDto;
import fr.seblaporte.kitchenvault.generated.model.RecipeSummaryDto;
import fr.seblaporte.kitchenvault.mapper.RecipeMapper;
import fr.seblaporte.kitchenvault.service.RecipeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ChatDelegate implements ChatApiDelegate {

    private static final Logger log = LoggerFactory.getLogger(ChatDelegate.class);

    private final RecipeSuggestionAgent recipeSuggestionAgent;
    private final PostgresChatMemoryStore chatMemoryStore;
    private final RecipeService recipeService;
    private final RecipeMapper recipeMapper;
    private final AtomicReference<String> currentSessionId = new AtomicReference<>(null);

    public ChatDelegate(RecipeSuggestionAgent recipeSuggestionAgent,
                        PostgresChatMemoryStore chatMemoryStore,
                        RecipeService recipeService,
                        RecipeMapper recipeMapper) {
        this.recipeSuggestionAgent = recipeSuggestionAgent;
        this.chatMemoryStore = chatMemoryStore;
        this.recipeService = recipeService;
        this.recipeMapper = recipeMapper;
    }

    @Override
    public ResponseEntity<ChatResponseDto> chatRecipe(ChatMessageDto chatMessageDto) {
        String sessionId = chatMessageDto.getSessionId();
        String prevSession = currentSessionId.getAndSet(sessionId);
        if (!sessionId.equals(prevSession)) {
            chatMemoryStore.deleteAllMessages();
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
