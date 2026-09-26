package fr.seblaporte.kitchenvault.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import fr.seblaporte.kitchenvault.entity.WeeklyPlanSession;
import fr.seblaporte.kitchenvault.repository.MealPlanEntryRepository;
import fr.seblaporte.kitchenvault.repository.WeeklyPlanSessionRepository;
import fr.seblaporte.kitchenvault.service.RecipeListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recipe retrieval for the weekly meal planner. Combines three mechanisms so the assistant stops
 * resurfacing the same recipes week after week (or within the same "propose something else"
 * back-and-forth):
 * <ul>
 *   <li>Recipes rejected by the user, or planned within the last {@code diversityLookbackDays}
 *       days, are excluded from every search (general, favorites, discovery alike) — a favorite
 *       used last week is just as much "off the table for now" as any other recently-used recipe,
 *       by design.</li>
 *   <li>The general search fetches a wider candidate band than it serves, then draws a weighted
 *       random sample from it (closer matches are more likely, but never guaranteed), so repeated
 *       near-identical queries don't return the exact same set every time.</li>
 *   <li>Recipes already proposed earlier in the same chat session (tracked on
 *       {@link WeeklyPlanSession}) are excluded too — a strict guarantee that asking for an
 *       alternative doesn't just repeat the previous answer.</li>
 * </ul>
 */
public class RoleAwareRecipeContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(RoleAwareRecipeContentRetriever.class);

    private static final String RECIPE_ID_KEY = "recipeId";
    private static final int GENERAL_POOL_SIZE = 30;
    private static final int GENERAL_SERVE_SIZE = 15;
    private static final double GENERAL_MIN_SCORE = 0.6;
    private static final int ROLE_MAX_RESULTS = 10;
    private static final double MIN_SAMPLING_WEIGHT = 1e-6;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final RecipeListService recipeListService;
    private final MealPlanEntryRepository mealPlanEntryRepository;
    private final WeeklyPlanSessionRepository weeklyPlanSessionRepository;
    private final int diversityLookbackDays;
    private final Random random;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoleAwareRecipeContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                           EmbeddingModel embeddingModel,
                                           RecipeListService recipeListService,
                                           MealPlanEntryRepository mealPlanEntryRepository,
                                           WeeklyPlanSessionRepository weeklyPlanSessionRepository,
                                           int diversityLookbackDays) {
        this(embeddingStore, embeddingModel, recipeListService, mealPlanEntryRepository,
                weeklyPlanSessionRepository, diversityLookbackDays, new Random());
    }

    RoleAwareRecipeContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                    EmbeddingModel embeddingModel,
                                    RecipeListService recipeListService,
                                    MealPlanEntryRepository mealPlanEntryRepository,
                                    WeeklyPlanSessionRepository weeklyPlanSessionRepository,
                                    int diversityLookbackDays,
                                    Random random) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.recipeListService = recipeListService;
        this.mealPlanEntryRepository = mealPlanEntryRepository;
        this.weeklyPlanSessionRepository = weeklyPlanSessionRepository;
        this.diversityLookbackDays = diversityLookbackDays;
        this.random = random;
    }

    @Override
    public List<Content> retrieve(Query query) {
        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();

        Set<String> excludedIds = union(
                recipeListService.getRejectedRecipeIds(),
                recentlyPlannedRecipeIds(),
                sessionProposedRecipeIds(query));

        Map<String, Content> byRecipeId = new LinkedHashMap<>();

        Filter exclusionFilter = excludedIds.isEmpty() ? null
                : MetadataFilterBuilder.metadataKey(RECIPE_ID_KEY).isNotIn(excludedIds);
        List<EmbeddingMatch<TextSegment>> generalPool =
                searchMatches(queryEmbedding, GENERAL_POOL_SIZE, GENERAL_MIN_SCORE, exclusionFilter);
        weightedSample(generalPool, GENERAL_SERVE_SIZE)
                .forEach(match -> byRecipeId.put(recipeId(match), Content.from(match.embedded())));

        tagRoleMatches(queryEmbedding, recipeListService.getFavoriteRecipeIds(), excludedIds,
                "recette favorite — source fiable", byRecipeId);
        tagRoleMatches(queryEmbedding, recipeListService.getDiscoveryRecipeIds(), excludedIds,
                "recette à découvrir", byRecipeId);

        return List.copyOf(byRecipeId.values());
    }

    private void tagRoleMatches(Embedding queryEmbedding, Set<String> roleIds, Set<String> excludedIds,
                                String label, Map<String, Content> byRecipeId) {
        if (roleIds.isEmpty()) {
            return;
        }
        Set<String> allowedIds = roleIds.stream()
                .filter(id -> !excludedIds.contains(id))
                .collect(Collectors.toSet());
        if (allowedIds.isEmpty()) {
            return;
        }
        Filter roleFilter = MetadataFilterBuilder.metadataKey(RECIPE_ID_KEY).isIn(allowedIds);
        searchMatches(queryEmbedding, ROLE_MAX_RESULTS, 0.0, roleFilter)
                .forEach(match -> byRecipeId.put(recipeId(match), tag(Content.from(match.embedded()), label)));
    }

    private List<EmbeddingMatch<TextSegment>> searchMatches(Embedding queryEmbedding, int maxResults,
                                                            double minScore, Filter filter) {
        var requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore);
        if (filter != null) {
            requestBuilder.filter(filter);
        }
        return embeddingStore.search(requestBuilder.build()).matches();
    }

    /**
     * Efraimidis-Spirakis weighted random sampling without replacement: each candidate gets a key
     * of {@code random()^(1/score)}, and the highest keys win. Closer matches (higher score) tend
     * to win more often, but the outcome isn't fixed — repeated calls with the same pool return
     * different subsets.
     */
    private List<EmbeddingMatch<TextSegment>> weightedSample(List<EmbeddingMatch<TextSegment>> pool, int limit) {
        if (pool.size() <= limit) {
            return pool;
        }
        return pool.stream()
                .sorted(Comparator.<EmbeddingMatch<TextSegment>>comparingDouble(match ->
                        Math.pow(random.nextDouble(), 1.0 / Math.max(match.score(), MIN_SAMPLING_WEIGHT)))
                        .reversed())
                .limit(limit)
                .toList();
    }

    private Set<String> recentlyPlannedRecipeIds() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(diversityLookbackDays);
        return Set.copyOf(mealPlanEntryRepository.findRecentRecipeIds(from, to));
    }

    private Set<String> sessionProposedRecipeIds(Query query) {
        if (query.metadata() == null || query.metadata().chatMemoryId() == null) {
            return Set.of();
        }
        String sessionId = query.metadata().chatMemoryId().toString();
        return weeklyPlanSessionRepository.findById(sessionId)
                .map(WeeklyPlanSession::getProposedRecipeIds)
                .map(this::deserializeProposedRecipeIds)
                .orElse(Set.of());
    }

    private Set<String> deserializeProposedRecipeIds(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            return Set.copyOf(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize proposed recipe ids: {}", e.getMessage());
            return Set.of();
        }
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> merged = new HashSet<>();
        for (Set<String> set : sets) {
            merged.addAll(set);
        }
        return merged;
    }

    private static String recipeId(EmbeddingMatch<TextSegment> match) {
        return match.embedded().metadata().getString(RECIPE_ID_KEY);
    }

    private static Content tag(Content content, String label) {
        TextSegment original = content.textSegment();
        TextSegment tagged = TextSegment.from(original.text() + " [Liste : " + label + "]", original.metadata());
        return Content.from(tagged);
    }
}
