package fr.seblaporte.kitchenvault.cookidoo.model;

import java.util.List;

public record AddRecipesToCalendarRequest(
        List<String> recipeIds,
        boolean replace
) {}
