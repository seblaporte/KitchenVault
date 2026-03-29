package fr.seblaporte.mycookidoo.cookidoo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CookidooRecipeDetails(
        String id,
        String name,
        String thumbnail,
        String image,
        String url,
        String difficulty,
        @JsonProperty("serving_size") int servingSize,
        @JsonProperty("active_time") int activeTime,
        @JsonProperty("total_time") int totalTime,
        List<String> notes,
        List<String> utensils,
        List<CookidooIngredient> ingredients,
        List<CookidooCategory> categories,
        @JsonProperty("nutrition_groups") List<CookidooNutritionGroup> nutritionGroups
) {}
