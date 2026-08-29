package fr.seblaporte.kitchenvault.ai.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
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
import fr.seblaporte.kitchenvault.service.RecipeListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class RoleAwareRecipeContentRetrieverTest {

    @Mock EmbeddingStore<TextSegment> embeddingStore;
    @Mock EmbeddingModel embeddingModel;
    @Mock RecipeListService recipeListService;

    private RoleAwareRecipeContentRetriever retriever;
    private final Embedding queryEmbedding = Embedding.from(new float[]{0.1f});

    @BeforeEach
    void setUp() {
        retriever = new RoleAwareRecipeContentRetriever(embeddingStore, embeddingModel, recipeListService);
        when(embeddingModel.embed("menu de la semaine")).thenReturn(Response.from(queryEmbedding));
        when(recipeListService.getRejectedRecipeIds()).thenReturn(Set.of());
        when(recipeListService.getFavoriteRecipeIds()).thenReturn(Set.of());
        when(recipeListService.getDiscoveryRecipeIds()).thenReturn(Set.of());
    }

    @Test
    void retrieve_noListsConfigured_runsOnlyTheUnfilteredGeneralSearch() {
        when(embeddingStore.search(any())).thenReturn(resultOf(match("r1", "Risotto")));

        List<Content> result = retriever.retrieve(Query.from("menu de la semaine"));

        ArgumentCaptor<EmbeddingSearchRequest> captor = ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore, times(1)).search(captor.capture());
        assertThat(captor.getValue().filter()).isNull();
        assertThat(captor.getValue().maxResults()).isEqualTo(10);
        assertThat(captor.getValue().minScore()).isEqualTo(0.6);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text()).isEqualTo("ID: r1. Recette: Risotto.");
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
