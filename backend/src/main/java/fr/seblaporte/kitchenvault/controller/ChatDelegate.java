package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.agent.RecipeSuggestionAgent;
import fr.seblaporte.kitchenvault.ai.memory.PostgresChatMemoryStore;
import fr.seblaporte.kitchenvault.generated.api.ChatApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.ChatMessageDto;
import fr.seblaporte.kitchenvault.generated.model.ChatResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class ChatDelegate implements ChatApiDelegate {

    private static final Logger log = LoggerFactory.getLogger(ChatDelegate.class);

    private final RecipeSuggestionAgent recipeSuggestionAgent;
    private final PostgresChatMemoryStore chatMemoryStore;
    private final AtomicReference<String> currentSessionId = new AtomicReference<>(null);

    public ChatDelegate(RecipeSuggestionAgent recipeSuggestionAgent, PostgresChatMemoryStore chatMemoryStore) {
        this.recipeSuggestionAgent = recipeSuggestionAgent;
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
    public ResponseEntity<ChatResponseDto> chatRecipe(ChatMessageDto chatMessageDto) {
        String sessionId = chatMessageDto.getSessionId();
        String prevSession = currentSessionId.getAndSet(sessionId);
        if (!sessionId.equals(prevSession)) {
            chatMemoryStore.deleteAllMessages();
        }
        try {
            String reply = recipeSuggestionAgent.suggestRecipes(sessionId, chatMessageDto.getMessage());
            return ResponseEntity.ok(new ChatResponseDto().reply(reply != null ? reply : ""));
        } catch (Exception e) {
            log.error("Recipe agent error for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

}
