package com.example.shortener.recorder;

import com.example.shortener.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** The cleanup SQL against a real Postgres (Testcontainers). */
@IntegrationTest
class ProcessedEventCleanupTest {

    @Autowired
    private ProcessedEventCleanup cleanup;

    @Autowired
    private JdbcTemplate jdbc;

    // P1: an event ID processed 2 days ago is deleted; one processed 1 hour ago is kept.
    @Test
    void deletesOnlyEventIdsOlderThanOneDay() {
        UUID oldId = insertProcessedAt(Instant.now().minusSeconds(2 * 24 * 60 * 60)); // 2 days ago
        UUID recentId = insertProcessedAt(Instant.now().minusSeconds(60 * 60)); // 1 hour ago

        cleanup.deleteOldEventIds();

        assertFalse(exists(oldId));
        assertTrue(exists(recentId));

    }

    private UUID insertProcessedAt(Instant processedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?)", id, Timestamp.from(processedAt));
        return id;
    }

    private boolean exists(UUID id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, id);
        return n != null && n > 0;
    }
}
