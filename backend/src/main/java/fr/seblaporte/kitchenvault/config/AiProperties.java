package fr.seblaporte.kitchenvault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

@ConfigurationProperties(prefix = "ai")
@Validated
public record AiProperties(
        @Valid @NotNull OvhProperties ovh,
        @Valid @NotNull OvhEmbeddingProperties ovhEmbedding,
        @Valid @NotNull ShoppingListProperties shoppingList,
        @Valid @NotNull WeeklyPlanProperties weeklyPlan
) {

    public record OvhProperties(
            String apiKey,
            @NotBlank String modelName,
            @NotBlank String baseUrl
    ) {}

    public record OvhEmbeddingProperties(
            String apiKey,
            @NotBlank String modelName,
            @NotBlank String baseUrl,
            int dimension
    ) {}

    public record ShoppingListProperties(
            @NotNull List<String> basicNecessities
    ) {}

    public record WeeklyPlanProperties(
            @Positive int diversityLookbackDays
    ) {}
}
