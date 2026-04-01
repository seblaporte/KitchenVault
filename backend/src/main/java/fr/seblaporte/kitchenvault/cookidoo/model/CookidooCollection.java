package fr.seblaporte.kitchenvault.cookidoo.model;

import java.util.List;

public record CookidooCollection(
        String id,
        String name,
        String description,
        List<CookidooChapter> chapters
) {}
