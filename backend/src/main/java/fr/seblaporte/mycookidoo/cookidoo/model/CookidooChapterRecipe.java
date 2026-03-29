package fr.seblaporte.mycookidoo.cookidoo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CookidooChapterRecipe(
        String id,
        String name,
        @JsonProperty("total_time") int totalTime
) {}
