package fr.seblaporte.kitchenvault.ai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import fr.seblaporte.kitchenvault.entity.Ingredient;
import fr.seblaporte.kitchenvault.entity.NutritionGroup;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(RecipeEmbeddingService.class);

    private final RecipeRepository recipeRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public RecipeEmbeddingService(
            RecipeRepository recipeRepository,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.recipeRepository = recipeRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    @Async
    @Transactional(readOnly = true)
    public void indexAllRecipes() {
        log.info("Starting recipe embedding indexation");
        try {
            List<Recipe> recipes = recipeRepository.findAll();

            List<TextSegment> segments = recipes.stream()
                    .map(this::toTextSegment)
                    .toList();

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

            embeddingStore.removeAll();
            embeddingStore.addAll(embeddings, segments);

            log.info("Indexed {} recipes", recipes.size());
        } catch (Exception e) {
            log.error("Failed to index recipes: {}", e.getMessage(), e);
        }
    }

    public List<EmbeddingMatch<TextSegment>> searchSimilar(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .build()).matches();
    }

    private TextSegment toTextSegment(Recipe recipe) {
        String ingredients = recipe.getIngredientGroups().stream()
                .flatMap(g -> g.getIngredients().stream())
                .map(Ingredient::getName)
                .collect(Collectors.joining(", "));

        StringBuilder text = new StringBuilder(
                String.format("Recette: %s. Difficulté: %s. Temps total: %d min. Ingrédients: %s. Ustensiles: %s.",
                        recipe.getName(),
                        recipe.getDifficulty() != null ? recipe.getDifficulty() : "inconnue",
                        recipe.getTotalTimeMinutes() != null ? recipe.getTotalTimeMinutes() : 0,
                        ingredients,
                        String.join(", ", recipe.getUtensils())));

        if (!recipe.getNutritionGroups().isEmpty()) {
            text.append(" Valeurs nutritionnelles:");
            for (NutritionGroup group : recipe.getNutritionGroups()) {
                String label = group.getQuantity() != null
                        ? String.format(" Pour %d %s", group.getQuantity(), group.getUnitNotation())
                        : " " + group.getName();
                String nutritionValues = group.getNutritions().stream()
                        .map(n -> String.format("%s: %s %s",
                                n.getType(),
                                n.getNumber().stripTrailingZeros().toPlainString(),
                                n.getUnitType()))
                        .collect(Collectors.joining(", "));
                text.append(String.format("%s (%s) –", label, group.getName()));
                text.append(" ").append(nutritionValues).append(".");
            }
        }

        return TextSegment.from(text.toString(), dev.langchain4j.data.document.Metadata.from("recipeId", recipe.getId()));
    }
}
