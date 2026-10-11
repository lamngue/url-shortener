package com.example.shortener;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(
        @NotBlank String baseUrl,
        // Long, not long: a missing value must fail validation instead of silently becoming 0.
        @NotNull(message = "k is required") Long shortCodeK) {
}
