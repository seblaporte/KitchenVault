package fr.seblaporte.kitchenvault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@ConfigurationProperties(prefix = "ai")
@Validated
public record AiProperties(
        @Valid @NotNull GroqProperties groq,
        @Valid @NotNull NomicProperties nomic,
        @Valid @NotNull ShoppingListProperties shoppingList
) {

    public record GroqProperties(
            String apiKey,
            @NotBlank String modelName
    ) {}

    public record NomicProperties(
            String apiKey,
            @NotBlank String modelName
    ) {}

    public record ShoppingListProperties(
            @NotNull List<String> basicNecessities
    ) {}
}
