package fr.seblaporte.kitchenvault.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "cookidoo")
@Validated
public record CookidooProperties(
        @Valid @NotNull ServiceProperties service,
        @Valid @NotNull SyncProperties sync
) {

    public record ServiceProperties(@NotBlank String url) {}

    public record SyncProperties(
            @NotBlank String cron,
            @Min(1) int resyncAfterHours
    ) {}
}
