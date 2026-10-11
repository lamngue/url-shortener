package com.example.shortener.recorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sends each click to Kafka without waiting for the result (D-04). If Kafka cannot take the
 * event within max.block.ms (100 ms, see application.yml), the click is dropped.
 */
@Component
public class KafkaClickRecorder implements ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(KafkaClickRecorder.class);

    private final KafkaTemplate<String, ClickEvent> kafka;

    public KafkaClickRecorder(KafkaTemplate<String, ClickEvent> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void record(String code) {
        ClickEvent event = new ClickEvent(UUID.randomUUID(), code, Instant.now());
        try {
            // send() returns at once; the result arrives later. We never wait for it.
            kafka.send(ClickTopicConfig.TOPIC, code, event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.debug("Click for {} was not delivered to Kafka", code, error);
                        }
                    });
        } catch (RuntimeException e) {
            // Thrown here when Kafka is unreachable for longer than max.block.ms.
            // Logged at DEBUG: while Kafka is down this happens on every redirect.
            log.debug("Click for {} dropped, Kafka unavailable", code, e);
        }
    }
}
