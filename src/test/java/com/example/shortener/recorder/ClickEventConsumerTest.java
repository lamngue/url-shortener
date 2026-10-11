package com.example.shortener.recorder;

import com.example.shortener.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void firstClickOfTheDayCreatesTheRow() {
        String code = uniqueCode();

        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, NOON));

        assertThat(clicks(code, LocalDate.parse("2026-10-11"))).isEqualTo(1);
    }

    @Test
    void laterClicksTheSameDayAddUp() {
        String code = uniqueCode();

        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, NOON));
        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, NOON.plusSeconds(60)));
        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, NOON.plusSeconds(120)));

        assertThat(clicks(code, LocalDate.parse("2026-10-11"))).isEqualTo(3);
    }

    @Test
    void redeliveredEventIsCountedOnce() {
        String code = uniqueCode();
        ClickEvent event = new ClickEvent(UUID.randomUUID(), code, NOON);

        consumer.onClick(event);
        consumer.onClick(event); // Kafka delivered it again, for example after a consumer crash

        assertThat(clicks(code, LocalDate.parse("2026-10-11"))).isEqualTo(1);
    }

    @Test
    void clickIsCountedOnTheUtcDayItHappened() {
        String code = uniqueCode();

        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, Instant.parse("2026-10-11T23:59:59Z")));
        consumer.onClick(new ClickEvent(UUID.randomUUID(), code, Instant.parse("2026-10-12T00:00:01Z")));

        assertThat(clicks(code, LocalDate.parse("2026-10-11"))).isEqualTo(1);
        assertThat(clicks(code, LocalDate.parse("2026-10-12"))).isEqualTo(1);
    }

    @Test
    void eventIdIsSavedForDeduplication() {
        UUID eventId = UUID.randomUUID();

        consumer.onClick(new ClickEvent(eventId, uniqueCode(), NOON));

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
        assertThat(rows).isEqualTo(1);
    }

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
