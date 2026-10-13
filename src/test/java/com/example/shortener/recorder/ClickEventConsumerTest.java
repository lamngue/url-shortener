package com.example.shortener.recorder;

import com.example.shortener.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The counting SQL against a real Postgres (Testcontainers). The Kafka listener is not started in
 * tests, so the consumer bean is called directly; it still runs inside its transaction.
 * Each test uses its own code, so tests do not affect each other.
 */
@IntegrationTest
class ClickEventConsumerTest {

    @Autowired
    private ClickEventConsumer consumer;

    @Autowired
    private JdbcTemplate jdbc;

    private static final Instant NOON = Instant.parse("2026-10-11T12:00:00Z");

    // C1: two different events for the same code on the same UTC day give num_clicks = 2.
    //     Covers both the first insert and the "add 1" path.
    @Test
    void clicksOnTheSameDayAddUp() {
        String code = uniqueCode();
        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, NOON));
        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, NOON));
        LocalDate day = LocalDate.ofInstant(NOON, ZoneOffset.UTC);
        assertEquals(2, clicks(code, day));
    }

    // C2: the same event (same eventId) delivered twice is counted once: num_clicks = 1.
    @Test
    void redeliveredEventIsCountedOnce() {
        String code = uniqueCode();
        UUID eventId = UUID.randomUUID();
        consumer.onClick(new ClickEvent(eventId, code, NOON));
        consumer.onClick(new ClickEvent(eventId, code, NOON));
        LocalDate day = LocalDate.ofInstant(NOON, ZoneOffset.UTC);
        assertEquals(1, clicks(code, day));
    }

    @Test
    void clicksOnDifferentDaysAreCountedSeparately() {
        String code = uniqueCode();
        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, NOON));
        Instant nextDayNoon = NOON.plusSeconds(24 * 60 * 60);
        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, nextDayNoon));
        LocalDate day1 = LocalDate.ofInstant(NOON, ZoneOffset.UTC);
        LocalDate day2 = LocalDate.ofInstant(nextDayNoon, ZoneOffset.UTC);
        assertEquals(1, clicks(code, day1));
        assertEquals(1, clicks(code, day2));
    }

    // Reads the count for one link on one day; 0 if there is no row yet.
    private long clicks(String code, LocalDate day) {
        Long n = jdbc.query("SELECT num_clicks FROM click_stats WHERE code = ? AND day = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, code, day);
        return n == null ? 0 : n;
    }

    // 7 characters, like a real code, and different for every test.
    private static String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }
}
