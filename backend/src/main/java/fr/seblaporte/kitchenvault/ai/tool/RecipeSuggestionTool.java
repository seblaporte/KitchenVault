package fr.seblaporte.kitchenvault.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import fr.seblaporte.kitchenvault.ai.service.RecipeEmbeddingService;
import fr.seblaporte.kitchenvault.entity.Ingredient;
import fr.seblaporte.kitchenvault.entity.Nutrition;
import fr.seblaporte.kitchenvault.entity.NutritionGroup;
import fr.seblaporte.kitchenvault.entity.Recipe;
import fr.seblaporte.kitchenvault.repository.RecipeRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class RecipeSuggestionTool {

    public record RecipeProposal(
            String recipeId,
            String name,
            String difficulty,
            int totalTimeMinutes,
            Integer caloriesPerServing,
            List<String> mainIngredients,
            List<String> requiredUtensils,
            double similarityScore
    ) {}

    private final RecipeEmbeddingService embeddingService;
    private final RecipeRepository recipeRepository;

    public RecipeSuggestionTool(RecipeEmbeddingService embeddingService, RecipeRepository recipeRepository) {
        this.embeddingService = embeddingService;
        this.recipeRepository = recipeRepository;
    }

    @Tool("""
            Suggère des recettes en utilisant la recherche sémantique.
            Retourne les recettes les plus pertinentes selon la requête, avec filtres optionnels.
            Utiliser RecipeId des résultats pour planifier des repas avec MealPlanTool.
            """)
    public List<RecipeProposal> suggestRecipes(
            @P("Description des recettes souhaitées (ingrédients, type de cuisine, occasion...)") String query,
            @P("Nombre de recettes à retourner") int count,
            @P(value = "Temps total maximum en minutes", required = false) Integer maxTotalTimeMinutes,
            @P(value = "Niveau de difficulté souhaité", required = false) String difficulty,
            @P(value = "Calories maximum par portion", required = false) Integer maxCaloriesPerServing,
            @P(value = "Ustensiles requis (vide = pas de contrainte)", required = false) List<String> requiredUtensils
    ) {
        List<EmbeddingMatch<TextSegment>> matches = embeddingService.searchSimilar(query, count * 3);

        List<RecipeProposal> results = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            String recipeId = match.embedded().metadata().getString("recipeId");
            if (recipeId == null) continue;

            Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
            if (recipeOpt.isEmpty()) continue;

            Recipe recipe = recipeOpt.get();

            if (maxTotalTimeMinutes != null && recipe.getTotalTimeMinutes() != null
                    && recipe.getTotalTimeMinutes() > maxTotalTimeMinutes) continue;

            if (difficulty != null && recipe.getDifficulty() != null
                    && !recipe.getDifficulty().equalsIgnoreCase(difficulty)) continue;

            if (requiredUtensils != null && !requiredUtensils.isEmpty()) {
                List<String> recipeUtensils = recipe.getUtensils().stream()
                        .map(String::toLowerCase).toList();
                boolean hasAll = requiredUtensils.stream()
                        .map(String::toLowerCase)
                        .allMatch(recipeUtensils::contains);
                if (!hasAll) continue;
            }

            Integer calories = extractCalories(recipe);
            if (maxCaloriesPerServing != null && calories != null && calories > maxCaloriesPerServing) continue;

            List<String> ingredients = recipe.getIngredientGroups().stream()
                    .flatMap(g -> g.getIngredients().stream())
                    .limit(5)
                    .map(Ingredient::getName)
                    .collect(Collectors.toList());

            results.add(new RecipeProposal(
                    recipe.getId(), recipe.getName(), recipe.getDifficulty(),
                    recipe.getTotalTimeMinutes() != null ? recipe.getTotalTimeMinutes() : 0,
                    calories, ingredients, recipe.getUtensils(), match.score()));

            if (results.size() >= count) break;
        }

        return results;
    }

    private Integer extractCalories(Recipe recipe) {
        return recipe.getNutritionGroups().stream()
                .flatMap(ng -> ng.getNutritions().stream())
                .filter(n -> n.getUnitType() != null
                        && (n.getUnitType().equalsIgnoreCase("kcal")
                        || n.getUnitType().equalsIgnoreCase("Cal")))
                .findFirst()
                .map(n -> n.getNumber() != null ? n.getNumber().intValue() : null)
                .orElse(null);
    }
}
