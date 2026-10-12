package com.example.shortener.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Where the identity provider's keys are, and what "iss" and "aud" must contain (D-05). */
@Validated
@ConfigurationProperties(prefix = "shortener.jwt")
public record JwtProperties(
        @NotBlank String jwkSetUri,
        @NotBlank String issuer,
        @NotBlank String audience) {
}
