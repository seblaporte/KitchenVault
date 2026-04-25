package fr.seblaporte.kitchenvault.ai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeEmbeddingServiceTest {

    @Mock RecipeRepository recipeRepository;
    @Mock EmbeddingModel embeddingModel;
    @Mock EmbeddingStore<TextSegment> embeddingStore;
    @InjectMocks RecipeEmbeddingService service;

    @Test
    void indexAllRecipes_emptyRepository_doesNotCallEmbeddingModel() {
        when(recipeRepository.findAll()).thenReturn(List.of());
        when(embeddingModel.embedAll(List.of())).thenReturn(Response.from(List.of()));

        service.indexAllRecipes();

        verify(embeddingStore).removeAll();
        verify(embeddingStore).addAll(List.of(), List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexAllRecipes_withRecipes_indexesAll() {
        Recipe recipe = new Recipe("r1");
        recipe.setName("Tarte aux pommes");
        recipe.setUtensils(new ArrayList<>());
        recipe.setNotes(new ArrayList<>());

        Embedding embedding = Embedding.from(new float[]{0.1f, 0.2f});
        when(recipeRepository.findAll()).thenReturn(List.of(recipe));
        when(embeddingModel.embedAll(any(List.class))).thenReturn(Response.from(List.of(embedding)));

        service.indexAllRecipes();

        verify(embeddingStore).removeAll();
        verify(embeddingStore).addAll(eq(List.of(embedding)), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchSimilar_delegatesToStoreSearch() {
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f});
        when(embeddingModel.embed(any(String.class))).thenReturn(Response.from(queryEmbedding));
        EmbeddingSearchResult<TextSegment> searchResult = mock(EmbeddingSearchResult.class);
        when(searchResult.matches()).thenReturn(List.of());
        when(embeddingStore.search(any())).thenReturn(searchResult);

        service.searchSimilar("recette rapide", 5);

        verify(embeddingStore).search(any());
    }
}
