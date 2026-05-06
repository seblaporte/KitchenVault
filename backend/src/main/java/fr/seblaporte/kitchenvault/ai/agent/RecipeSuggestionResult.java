package fr.seblaporte.kitchenvault.ai.agent;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("résultat de la suggestion de recettes")
public record RecipeSuggestionResult(
        @Description("réponse textuelle de l'assistant à destination de l'utilisateur")
        String reply,

        @Description("liste des IDs des recettes suggérées, tels qu'ils apparaissent dans le contexte RAG (champ 'ID:'). Liste vide si aucune recette précise n'est identifiée.")
        List<String> recipeIds
) {}
