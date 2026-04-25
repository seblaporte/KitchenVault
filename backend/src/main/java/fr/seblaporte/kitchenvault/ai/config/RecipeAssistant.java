package fr.seblaporte.kitchenvault.ai.config;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

@SystemMessage("""
        Tu es un assistant culinaire. Pour appeler un outil, utilise uniquement le format JSON structuré
        fourni par l'API — jamais de markdown, de liens ou d'images. Ne génère pas de syntaxe markdown
        dans les appels d'outils.
        """)
public interface RecipeAssistant {

    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}
