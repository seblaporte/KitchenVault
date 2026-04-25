package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.config.RecipeAssistant;
import fr.seblaporte.kitchenvault.generated.api.ChatApiDelegate;
import fr.seblaporte.kitchenvault.generated.model.ChatMessageDto;
import fr.seblaporte.kitchenvault.generated.model.ChatResponseDto;
import fr.seblaporte.kitchenvault.generated.model.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ChatDelegate implements ChatApiDelegate {

    private static final Logger log = LoggerFactory.getLogger(ChatDelegate.class);

    private final RecipeAssistant assistant;

    public ChatDelegate(RecipeAssistant assistant) {
        this.assistant = assistant;
    }

    @Override
    public ResponseEntity<ChatResponseDto> sendMessage(ChatMessageDto chatMessageDto) {
        try {
            String reply = assistant.chat(chatMessageDto.getSessionId(), chatMessageDto.getMessage());
            return ResponseEntity.ok(new ChatResponseDto().reply(reply != null ? reply : ""));
        } catch (Exception e) {
            log.error("AI service error for session {}: {}", chatMessageDto.getSessionId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .build();
        }
    }
}
