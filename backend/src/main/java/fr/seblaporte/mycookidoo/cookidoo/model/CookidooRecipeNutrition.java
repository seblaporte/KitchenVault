package fr.seblaporte.mycookidoo.cookidoo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CookidooRecipeNutrition(
        List<CookidooNutrition> nutritions,
        int quantity,
        @JsonProperty("unit_notation") String unitNotation
) {}
