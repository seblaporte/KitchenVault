package fr.seblaporte.kitchenvault.cookidoo.model;

import java.util.List;

public record CookidooChapter(
        String name,
        List<CookidooChapterRecipe> recipes
) {}
