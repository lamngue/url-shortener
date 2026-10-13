package com.example.shortener.recorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps processed_events small (D-04). Event IDs older than 1 day are no longer needed.
 * Every app instance runs this; that is harmless, because the DELETE is idempotent.
 */
@Component
public class ProcessedEventCleanup {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanup.class);

    private final JdbcTemplate jdbc;

    public ProcessedEventCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Every hour, starting one minute after startup.
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void deleteOldEventIds() {
        int deleted = jdbc.update(
            "DELETE FROM processed_events WHERE processed_at < NOW() - INTERVAL '1 day'"
        );
        log.info("Deleted {} old processed events", deleted);
    }
}
