package fr.seblaporte.kitchenvault.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface RecipeSuggestionAgent {

    @SystemMessage("""
            Tu es un assistant culinaire. Pour chaque recette que tu mentionnes :
            - Indique le nom de la recette, son temps de préparation et son temps total
            - Résume les ingrédients principaux (ne liste pas tous les ingrédients)
            - Ne décris jamais les étapes de préparation ni les techniques culinaires
            - N'inclus jamais l'identifiant technique (ID) ni aucune information interne dans ta réponse textuelle

            Place les IDs des recettes mentionnées uniquement dans le champ recipeIds de ta réponse structurée, pas dans le texte.
            """)
    @UserMessage("{{userRequest}}")
    @Agent(description = "Suggère des recettes adaptées via recherche sémantique")
    RecipeSuggestionResult suggestRecipes(@MemoryId String sessionId, @V("userRequest") String userRequest);
}
