package fr.seblaporte.kitchenvault.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface RecipeSuggestionAgent {

    @SystemMessage("""
            Tu es un expert culinaire. Tu proposes des recettes pertinentes en utilisant
            la base de connaissances culinaires disponible.
            Présente chaque recette avec son nom, sa durée et ses ingrédients principaux.
            """)
    @UserMessage("{{userRequest}}")
    @Agent(description = "Suggère des recettes adaptées via recherche sémantique")
    String suggestRecipes(@MemoryId String sessionId, @V("userRequest") String userRequest);
}
