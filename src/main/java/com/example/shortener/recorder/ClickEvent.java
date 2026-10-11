package com.example.shortener.recorder;

import java.time.Instant;
import java.util.UUID;

/**
 * One click, as sent to Kafka. eventId lets the consumer ignore a redelivered event, and
 * clickedAt decides which day the click is counted on.
 */
public record ClickEvent(UUID eventId, String code, Instant clickedAt) {
}
