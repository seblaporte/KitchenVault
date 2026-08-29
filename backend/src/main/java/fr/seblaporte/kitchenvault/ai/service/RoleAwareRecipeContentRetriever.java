package fr.seblaporte.kitchenvault.ai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import fr.seblaporte.kitchenvault.service.RecipeListService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recipe retrieval for the weekly meal planner. Rejected-list recipes are excluded directly at
 * the vector search level (metadata filter), so they are never presented to the LLM and never
 * need to be enumerated. Favorites/discovery matches are fetched with a dedicated, bounded,
 * filtered search and tagged inline, instead of dumping the full lists as text — their size is
 * user-curated and can grow arbitrarily, so nothing here scales with list length.
 */
public class RoleAwareRecipeContentRetriever implements ContentRetriever {

    private static final String RECIPE_ID_KEY = "recipeId";
    private static final int GENERAL_MAX_RESULTS = 10;
    private static final double GENERAL_MIN_SCORE = 0.6;
    private static final int ROLE_MAX_RESULTS = 10;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final RecipeListService recipeListService;

    public RoleAwareRecipeContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                           EmbeddingModel embeddingModel,
                                           RecipeListService recipeListService) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.recipeListService = recipeListService;
    }

    @Override
    public List<Content> retrieve(Query query) {
        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();

        Set<String> rejectedIds = recipeListService.getRejectedRecipeIds();
        Map<String, Content> byRecipeId = new LinkedHashMap<>();

        Filter exclusionFilter = rejectedIds.isEmpty() ? null
                : MetadataFilterBuilder.metadataKey(RECIPE_ID_KEY).isNotIn(rejectedIds);
        search(queryEmbedding, GENERAL_MAX_RESULTS, GENERAL_MIN_SCORE, exclusionFilter)
                .forEach(content -> byRecipeId.put(recipeId(content), content));

        tagRoleMatches(queryEmbedding, recipeListService.getFavoriteRecipeIds(), rejectedIds,
                "recette favorite — source fiable", byRecipeId);
        tagRoleMatches(queryEmbedding, recipeListService.getDiscoveryRecipeIds(), rejectedIds,
                "recette à découvrir", byRecipeId);

        return List.copyOf(byRecipeId.values());
    }

    private void tagRoleMatches(Embedding queryEmbedding, Set<String> roleIds, Set<String> rejectedIds,
                                String label, Map<String, Content> byRecipeId) {
        if (roleIds.isEmpty()) {
            return;
        }
        Set<String> allowedIds = roleIds.stream()
                .filter(id -> !rejectedIds.contains(id))
                .collect(Collectors.toSet());
        if (allowedIds.isEmpty()) {
            return;
        }
        Filter roleFilter = MetadataFilterBuilder.metadataKey(RECIPE_ID_KEY).isIn(allowedIds);
        search(queryEmbedding, ROLE_MAX_RESULTS, 0.0, roleFilter)
                .forEach(content -> byRecipeId.put(recipeId(content), tag(content, label)));
    }

    private List<Content> search(Embedding queryEmbedding, int maxResults, double minScore, Filter filter) {
        var requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore);
        if (filter != null) {
            requestBuilder.filter(filter);
        }
        return embeddingStore.search(requestBuilder.build()).matches().stream()
                .map(EmbeddingMatch::embedded)
                .map(Content::from)
                .toList();
    }

    private static String recipeId(Content content) {
        return content.textSegment().metadata().getString(RECIPE_ID_KEY);
    }

    private static Content tag(Content content, String label) {
        TextSegment original = content.textSegment();
        TextSegment tagged = TextSegment.from(original.text() + " [Liste : " + label + "]", original.metadata());
        return Content.from(tagged);
    }
}
