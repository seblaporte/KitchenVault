package fr.seblaporte.kitchenvault.cookidoo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AddRecipesToCalendarRequest(
        @JsonProperty("recipe_ids") List<String> recipeIds,
        boolean replace
) {}
