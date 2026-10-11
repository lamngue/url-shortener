package com.example.shortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts only ShortenerConfig (no database), to check how startup reacts to the K setting.
 */
class ShortenerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ShortenerConfig.class)
            .withPropertyValues("shortener.base-url=http://localhost:8080");

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
}
