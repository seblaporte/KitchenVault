package fr.seblaporte.mycookidoo.cookidoo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CookidooNutritionGroup(
        String name,
        @JsonProperty("recipe_nutritions") List<CookidooRecipeNutrition> recipeNutritions
) {}
