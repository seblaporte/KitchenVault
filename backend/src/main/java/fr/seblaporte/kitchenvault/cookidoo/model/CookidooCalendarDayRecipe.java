package fr.seblaporte.kitchenvault.cookidoo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CookidooCalendarDayRecipe(
        String id,
        String name,
        @JsonProperty("total_time") int totalTime,
        String thumbnail,
        String image,
        String url
) {}
