package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.ai.service.RecipeEmbeddingService;
import fr.seblaporte.kitchenvault.generated.api.EmbeddingApiDelegate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingDelegate implements EmbeddingApiDelegate {

    private final RecipeEmbeddingService recipeEmbeddingService;

    public EmbeddingDelegate(RecipeEmbeddingService recipeEmbeddingService) {
        this.recipeEmbeddingService = recipeEmbeddingService;
    }

    @Override
    public ResponseEntity<Void> triggerEmbeddingIndexation() {
        recipeEmbeddingService.indexAllRecipes();
        return ResponseEntity.accepted().build();
    }
}
