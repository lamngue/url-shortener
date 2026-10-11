package com.example.shortener.cache;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "shortener.cache")
public record LinkCacheProperties(
        // How long a code -> URL entry lives. Bounds how long a stale entry can survive.
        @NotNull Duration positiveTtl,
        // How long a "not found" entry lives. Short, so it cannot hide a link created later.
        @NotNull Duration negativeTtl,
        // Extra random share added to positiveTtl (0.1 = up to 10% longer), so entries expire spread out.
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double jitter) {
}
