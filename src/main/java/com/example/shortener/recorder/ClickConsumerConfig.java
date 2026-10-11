package com.example.shortener.recorder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class ClickConsumerConfig {

    // If counting fails (for example Postgres is down), retry the same event every second for as
    // long as it takes, instead of Spring's default of a few tries and then skipping it, which
    // would silently lose clicks. Events that can never succeed are skipped and logged:
    // broken messages (handled by ErrorHandlingDeserializer, never retried) and bad data.
    @Bean
    DefaultErrorHandler clickErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(DataIntegrityViolationException.class);
        return handler;
    }
}
