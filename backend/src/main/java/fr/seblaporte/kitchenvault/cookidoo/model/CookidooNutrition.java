package fr.seblaporte.kitchenvault.cookidoo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CookidooNutrition(
        double number,
        String type,
        @JsonProperty("unit_type") String unitType
) {}
