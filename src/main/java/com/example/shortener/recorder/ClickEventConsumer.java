package com.example.shortener.recorder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Counts click events per link per day (D-04). Kafka delivers at least once, so the same event
 * can arrive twice; processed_events makes sure it is counted only once.
 */
@Component
public class ClickEventConsumer {

    private final JdbcTemplate jdbc;

    public ClickEventConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // One transaction: either the event ID is saved AND the count goes up, or neither happens.
    // The offset is committed to Kafka only after this method returns, so a crash before that
    // means the event is read again, and the event ID check skips it.
    @KafkaListener(topics = ClickTopicConfig.TOPIC, groupId = "click-stats")
    @Transactional
    public void onClick(ClickEvent event) {
        int inserted = jdbc.update(
                "INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                event.eventId());
        if (inserted == 0) {
            return; // already counted: a redelivered event
        }
        // The day the click happened (UTC), not the day we happen to read it.
        LocalDate day = LocalDate.ofInstant(event.clickedAt(), ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO click_stats (code, day, num_clicks) VALUES (?, ?, 1)
                ON CONFLICT (code, day) DO UPDATE SET num_clicks = click_stats.num_clicks + 1
                """, event.code(), day);
    }
}
