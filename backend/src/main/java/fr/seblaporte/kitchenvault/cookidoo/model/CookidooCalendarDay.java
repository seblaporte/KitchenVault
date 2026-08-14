package fr.seblaporte.kitchenvault.cookidoo.model;

import java.util.List;

public record CookidooCalendarDay(
        String id,
        String title,
        List<CookidooCalendarDayRecipe> recipes
) {}
