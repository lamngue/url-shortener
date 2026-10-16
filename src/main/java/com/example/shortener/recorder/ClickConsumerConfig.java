package com.example.shortener.recorder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

// @EnableScheduling turns on @Scheduled methods, used by ProcessedEventCleanup.
@Configuration
@EnableScheduling
public class ClickConsumerConfig {

    // Retry a failed event every second until it succeeds (e.g. Postgres down), instead of
    // Spring's default of a few tries and then skipping it. Bad data is skipped, not retried.
    @Bean
    DefaultErrorHandler clickErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(DataIntegrityViolationException.class);
        return handler;
    }
}
