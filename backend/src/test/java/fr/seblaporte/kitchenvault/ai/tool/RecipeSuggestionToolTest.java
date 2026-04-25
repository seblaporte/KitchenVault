package fr.seblaporte.kitchenvault.ai.tool;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import fr.seblaporte.kitchenvault.ai.service.RecipeEmbeddingService;
import fr.seblaporte.kitchenvault.ai.tool.RecipeSuggestionTool.RecipeProposal;
import fr.seblaporte.kitchenvault.entity.*;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeSuggestionToolTest {

    @Mock RecipeEmbeddingService embeddingService;
    @Mock RecipeRepository recipeRepository;
    @InjectMocks RecipeSuggestionTool tool;

    @Test
    void suggestRecipes_noFilters_returnsMatchingRecipes() {
        Recipe recipe = recipe("r1", "Poulet rôti", 45, "easy", null);
        when(embeddingService.searchSimilar("poulet", 3)).thenReturn(List.of(match("r1", 0.9)));
        when(recipeRepository.findById("r1")).thenReturn(Optional.of(recipe));

        List<RecipeProposal> results = tool.suggestRecipes("poulet", 1, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Poulet rôti");
        assertThat(results.get(0).similarityScore()).isEqualTo(0.9);
    }

    @Test
    void suggestRecipes_timeTooLong_filtered() {
        Recipe recipe = recipe("r1", "Bœuf bourguignon", 180, null, null);
        when(embeddingService.searchSimilar("bœuf", 3)).thenReturn(List.of(match("r1", 0.8)));
        when(recipeRepository.findById("r1")).thenReturn(Optional.of(recipe));

        List<RecipeProposal> results = tool.suggestRecipes("bœuf", 1, 60, null, null, null);

        assertThat(results).isEmpty();
    }

    @Test
    void suggestRecipes_difficultyMismatch_filtered() {
        Recipe recipe = recipe("r1", "Soufflé", 60, "hard", null);
        when(embeddingService.searchSimilar("soufflé", 3)).thenReturn(List.of(match("r1", 0.85)));
        when(recipeRepository.findById("r1")).thenReturn(Optional.of(recipe));

        List<RecipeProposal> results = tool.suggestRecipes("soufflé", 1, null, "easy", null, null);

        assertThat(results).isEmpty();
    }

    @Test
    void suggestRecipes_caloriesAboveMax_filtered() {
        Recipe recipe = recipe("r1", "Pizza", 30, null, 800);
        when(embeddingService.searchSimilar("pizza", 3)).thenReturn(List.of(match("r1", 0.9)));
        when(recipeRepository.findById("r1")).thenReturn(Optional.of(recipe));

        List<RecipeProposal> results = tool.suggestRecipes("pizza", 1, null, null, 500, null);

        assertThat(results).isEmpty();
    }

    @Test
    void suggestRecipes_missingUtensil_filtered() {
        Recipe recipe = recipe("r1", "Risotto", 40, null, null);
        recipe.setUtensils(List.of("Thermomix"));
        when(embeddingService.searchSimilar("risotto", 3)).thenReturn(List.of(match("r1", 0.88)));
        when(recipeRepository.findById("r1")).thenReturn(Optional.of(recipe));

        List<RecipeProposal> results = tool.suggestRecipes("risotto", 1, null, null, null, List.of("Cocotte", "Thermomix"));

        assertThat(results).isEmpty();
    }

    @Test
    void suggestRecipes_recipeNotFound_skipped() {
        when(embeddingService.searchSimilar("test", 3)).thenReturn(List.of(match("missing", 0.9)));
        when(recipeRepository.findById("missing")).thenReturn(Optional.empty());

        List<RecipeProposal> results = tool.suggestRecipes("test", 1, null, null, null, null);

        assertThat(results).isEmpty();
    }

    private Recipe recipe(String id, String name, int totalTime, String difficulty, Integer kcal) {
        Recipe r = new Recipe(id);
        r.setName(name);
        r.setTotalTimeMinutes(totalTime);
        r.setDifficulty(difficulty);
        r.setUtensils(new ArrayList<>());
        r.setNotes(new ArrayList<>());

        if (kcal != null) {
            NutritionGroup ng = new NutritionGroup(r, "Énergie", 1, "par portion");
            Nutrition n = new Nutrition(ng, "energy", BigDecimal.valueOf(kcal), "kcal");
            ng.setNutritions(List.of(n));
            r.getNutritionGroups().add(ng);
        }
        return r;
    }

    private EmbeddingMatch<TextSegment> match(String recipeId, double score) {
        TextSegment segment = TextSegment.from("text", Metadata.from("recipeId", recipeId));
        return new EmbeddingMatch<>(score, "id-" + recipeId, null, segment);
    }
}
