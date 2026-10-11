package com.example.shortener.config;

import com.example.shortener.cache.LinkCacheProperties;
import com.example.shortener.shortcode.ShortCodeGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts only ShortenerConfig (no database), to check how startup reacts to the K and cache settings.
 */
class ShortenerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ShortenerConfig.class)
            .withPropertyValues(
                    "shortener.base-url=http://localhost:8080",
                    "shortener.cache.positive-ttl=5m",
                    "shortener.cache.negative-ttl=10s",
                    "shortener.cache.jitter=0.1");

    @Test
    void missingKFailsStartupWithKIsRequired() {
        // An empty value is what ${SHORT_CODE_K:} resolves to when the variable is not set.
        runner.withPropertyValues("shortener.short-code-k=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("k is required");
                });
    }

    @Test
    void invalidKFailsStartup() {
        runner.withPropertyValues("shortener.short-code-k=2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("coprime");
                });
    }

    @Test
    void validKCreatesTheGenerator() {
        runner.withPropertyValues("shortener.short-code-k=2654435761")
                .run(context -> assertThat(context).hasSingleBean(ShortCodeGenerator.class));
    }

    @Test
    void cacheSettingsAreBound() {
        runner.withPropertyValues("shortener.short-code-k=2654435761")
                .run(context -> {
                    LinkCacheProperties cache = context.getBean(LinkCacheProperties.class);
                    assertThat(cache.positiveTtl()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(cache.negativeTtl()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(cache.jitter()).isEqualTo(0.1);
                });
    }

    @Test
    void jitterAboveOneFailsStartup() {
        runner.withPropertyValues("shortener.short-code-k=2654435761", "shortener.cache.jitter=1.5")
                .run(context -> assertThat(context).hasFailed());
    }
}
