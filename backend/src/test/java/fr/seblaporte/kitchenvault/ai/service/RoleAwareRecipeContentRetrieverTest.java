package fr.seblaporte.kitchenvault.ai.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import fr.seblaporte.kitchenvault.entity.WeeklyPlanSession;
import fr.seblaporte.kitchenvault.repository.MealPlanEntryRepository;
import fr.seblaporte.kitchenvault.repository.WeeklyPlanSessionRepository;
import fr.seblaporte.kitchenvault.service.RecipeListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class RoleAwareRecipeContentRetrieverTest {

    private static final int LOOKBACK_DAYS = 28;

    @Mock EmbeddingStore<TextSegment> embeddingStore;
    @Mock EmbeddingModel embeddingModel;
    @Mock RecipeListService recipeListService;
    @Mock MealPlanEntryRepository mealPlanEntryRepository;
    @Mock WeeklyPlanSessionRepository weeklyPlanSessionRepository;

    private RoleAwareRecipeContentRetriever retriever;
    private final Embedding queryEmbedding = Embedding.from(new float[]{0.1f});

    @BeforeEach
    void setUp() {
        retriever = new RoleAwareRecipeContentRetriever(embeddingStore, embeddingModel, recipeListService,
                mealPlanEntryRepository, weeklyPlanSessionRepository, LOOKBACK_DAYS, new Random());
        when(embeddingModel.embed("menu de la semaine")).thenReturn(Response.from(queryEmbedding));
        when(recipeListService.getRejectedRecipeIds()).thenReturn(Set.of());
        when(recipeListService.getFavoriteRecipeIds()).thenReturn(Set.of());
        when(recipeListService.getDiscoveryRecipeIds()).thenReturn(Set.of());
        when(mealPlanEntryRepository.findRecentRecipeIds(any(), any())).thenReturn(List.of());
    }

    @Test
    void retrieve_noListsConfigured_runsOnlyTheUnfilteredGeneralSearch() {
        when(embeddingStore.search(any())).thenReturn(resultOf(match("r1", "Risotto")));

        List<Content> result = retriever.retrieve(Query.from("menu de la semaine"));

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore, times(1)).search(captor.capture());
        assertThat(captor.getValue().filter()).isNull();
        assertThat(captor.getValue().maxResults()).isEqualTo(30);
        assertThat(captor.getValue().minScore()).isEqualTo(0.6);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text()).isEqualTo("ID: r1. Recette: Risotto.");
    }

    @Test
    void retrieve_generalPoolLargerThanServeSize_samplesDownToServeSize() {
        List<EmbeddingMatch<TextSegment>> matches = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> match("r" + i, "Recette " + i))
                .toList();
        when(embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(matches));

        List<Content> result = retriever.retrieve(Query.from("menu de la semaine"));

        assertThat(result).hasSize(15);
    }

    @Test
    void retrieve_withRejectedRecipes_generalSearchExcludesThemViaFilter() {
        when(recipeListService.getRejectedRecipeIds()).thenReturn(Set.of("bad-1", "bad-2"));
        when(embeddingStore.search(any())).thenReturn(resultOf(match("r1", "Risotto")));

        retriever.retrieve(Query.from("menu de la semaine"));

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore, times(1)).search(captor.capture());
        assertThat(captor.getValue().filter())
                .isEqualTo(MetadataFilterBuilder.metadataKey("recipeId").isNotIn(Set.of("bad-1", "bad-2")));
    }

    @Test
    void retrieve_withRecentlyPlannedRecipes_generalSearchExcludesThemViaFilter() {
        when(mealPlanEntryRepository.findRecentRecipeIds(any(), any())).thenReturn(List.of("recent-1", "recent-2"));
        when(embeddingStore.search(any())).thenReturn(resultOf(match("r1", "Risotto")));

        retriever.retrieve(Query.from("menu de la semaine"));

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore, times(1)).search(captor.capture());
        assertThat(captor.getValue().filter())
                .isEqualTo(MetadataFilterBuilder.metadataKey("recipeId").isNotIn(Set.of("recent-1", "recent-2")));
    }

    @Test
    void retrieve_computesLookbackWindowFromConfiguredDays() {
        retriever = new RoleAwareRecipeContentRetriever(embeddingStore, embeddingModel, recipeListService,
                mealPlanEntryRepository, weeklyPlanSessionRepository, 14, new Random());
        when(embeddingStore.search(any())).thenReturn(resultOf());

        retriever.retrieve(Query.from("menu de la semaine"));

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(mealPlanEntryRepository).findRecentRecipeIds(fromCaptor.capture(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.now());
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.now().minusDays(14));
    }

    @Test
    void retrieve_favoriteMatch_isFetchedAndTaggedInline() {
        when(recipeListService.getFavoriteRecipeIds()).thenReturn(Set.of("fav-1"));
        when(embeddingStore.search(any())).thenReturn(
                resultOf(), // general search: nothing relevant
                resultOf(match("fav-1", "Tarte aux pommes")));

        List<Content> result = retriever.retrieve(Query.from("menu de la semaine"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text())
                .isEqualTo("ID: fav-1. Recette: Tarte aux pommes. [Liste : recette favorite — source fiable]");

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore, times(2)).search(captor.capture());
        EmbeddingSearchRequest favoriteRequest = captor.getAllValues().get(1);
        assertThat(favoriteRequest.filter())
                .isEqualTo(MetadataFilterBuilder.metadataKey("recipeId").isIn(Set.of("fav-1")));
        assertThat(favoriteRequest.minScore()).isEqualTo(0.0);
    }

    @Test
    void retrieve_favoriteRecentlyPlanned_isExcludedFromFavoriteSearch() {
        // Favorites are no longer exempt from the recency rotation: a favorite planned recently
        // must disappear from the pool just like any other recipe (explicit product decision).
        when(recipeListService.getFavoriteRecipeIds()).thenReturn(Set.of("fav-1"));
        when(mealPlanEntryRepository.findRecentRecipeIds(any(), any())).thenReturn(List.of("fav-1"));
        when(embeddingStore.search(any())).thenReturn(resultOf());

        retriever.retrieve(Query.from("menu de la semaine"));

        // Only the general search runs — the favorite-role search is skipped since its only
        // candidate is excluded by recency.
        verify(embeddingStore, times(1)).search(any());
    }

    @Test
    void retrieve_discoveryMatch_isFetchedAndTaggedInline() {
        when(recipeListService.getDiscoveryRecipeIds()).thenReturn(Set.of("disc-1"));
        when(embeddingStore.search(any())).thenReturn(
                resultOf(),
                resultOf(match("disc-1", "Curry de légumes")));

        List<Content> result = retriever.retrieve(Query.from("menu de la semaine"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text())
                .isEqualTo("ID: disc-1. Recette: Curry de légumes. [Liste : recette à découvrir]");
    }

    @Test
    void retrieve_recipeInBothGeneralAndFavoriteResults_isDeduplicatedAndTagged() {
        when(recipeListService.getFavoriteRecipeIds()).thenReturn(Set.of("r1"));
        when(embeddingStore.search(any())).thenReturn(
                resultOf(match("r1", "Risotto")),
                resultOf(match("r1", "Risotto")));

        List<Content> result = retriever.retrieve(Query.from("menu de la semaine"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text()).contains("[Liste : recette favorite");
    }

    @Test
    void retrieve_favoriteAlsoRejected_isExcludedAndRoleSearchSkipped() {
        when(recipeListService.getRejectedRecipeIds()).thenReturn(Set.of("fav-1"));
        when(recipeListService.getFavoriteRecipeIds()).thenReturn(Set.of("fav-1"));
        when(embeddingStore.search(any())).thenReturn(resultOf());

        retriever.retrieve(Query.from("menu de la semaine"));

        // Only the general search runs — the favorite-role search is skipped entirely since its
        // only candidate is also rejected, so there is nothing left to search for.
        verify(embeddingStore, times(1)).search(any());
    }

    @Test
    void retrieve_emptyFavoriteAndDiscoveryLists_doesNotPerformRoleSearches() {
        when(embeddingStore.search(any())).thenReturn(resultOf());

        retriever.retrieve(Query.from("menu de la semaine"));

        verify(embeddingStore, times(1)).search(any());
    }

    @Test
    void retrieve_noSessionMetadata_skipsSessionProposedLookup() {
        when(embeddingStore.search(any())).thenReturn(resultOf());

        retriever.retrieve(Query.from("menu de la semaine"));

        verifyNoInteractions(weeklyPlanSessionRepository);
    }

    @Test
    void retrieve_sessionHasProposedRecipes_generalSearchExcludesThemViaFilter() {
        Query query = Query.from("menu de la semaine",
                dev.langchain4j.rag.query.Metadata.from(UserMessage.from("menu de la semaine"), "session-1", List.of()));
        when(weeklyPlanSessionRepository.findById("session-1"))
                .thenReturn(Optional.of(sessionWithProposedIds("session-1", "prev-1", "prev-2")));
        when(embeddingStore.search(any())).thenReturn(resultOf(match("r1", "Risotto")));

        retriever.retrieve(query);

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore, times(1)).search(captor.capture());
        assertThat(captor.getValue().filter())
                .isEqualTo(MetadataFilterBuilder.metadataKey("recipeId").isNotIn(Set.of("prev-1", "prev-2")));
    }

    private static WeeklyPlanSession sessionWithProposedIds(String sessionId, String... recipeIds) {
        WeeklyPlanSession session = new WeeklyPlanSession(sessionId, LocalDate.now());
        String json = "[" + String.join(",", java.util.Arrays.stream(recipeIds)
                .map(id -> "\"" + id + "\"").toArray(String[]::new)) + "]";
        session.setProposedRecipeIds(json);
        return session;
    }

    private static EmbeddingMatch<TextSegment> match(String recipeId, String name) {
        TextSegment segment = TextSegment.from(
                "ID: " + recipeId + ". Recette: " + name + ".",
                Metadata.from("recipeId", recipeId));
        return new EmbeddingMatch<>(0.9, "embedding-" + recipeId, Embedding.from(new float[]{0.1f}), segment);
    }

    private static EmbeddingSearchResult<TextSegment> resultOf(EmbeddingMatch<TextSegment>... matches) {
        return new EmbeddingSearchResult<>(List.of(matches));
    }
}
