package com.example.shortener.recorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Keeps processed_events small (D-04). A redelivered event arrives within minutes, so event IDs
 * older than 1 day are no longer needed. Every app instance runs this; that is harmless, because
 * deleting rows that are already gone does nothing.
 */
@Component
public class ProcessedEventCleanup {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanup.class);

    static final Duration KEEP = Duration.ofDays(1);

    private final JdbcTemplate jdbc;

    public ProcessedEventCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Every hour, starting one minute after startup.
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void deleteOldEventIds() {
        int deleted = deleteOlderThan(Instant.now().minus(KEEP));
        log.info("Deleted {} processed event IDs older than {}", deleted, KEEP);
    }

    int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM processed_events WHERE processed_at < ?", Timestamp.from(cutoff));
    }
}
