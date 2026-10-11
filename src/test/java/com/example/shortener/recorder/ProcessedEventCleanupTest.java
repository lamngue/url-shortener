package com.example.shortener.recorder;

import com.example.shortener.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The cleanup SQL against a real Postgres (Testcontainers). */
@IntegrationTest
class ProcessedEventCleanupTest {

    @Autowired
    private ProcessedEventCleanup cleanup;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletesOnlyEventIdsOlderThanTheCutoff() {
        Instant now = Instant.now();
        UUID old = insertProcessedAt(now.minus(Duration.ofDays(2)));
        UUID recent = insertProcessedAt(now.minus(Duration.ofHours(1)));

        cleanup.deleteOlderThan(now.minus(ProcessedEventCleanup.KEEP));

        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
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
