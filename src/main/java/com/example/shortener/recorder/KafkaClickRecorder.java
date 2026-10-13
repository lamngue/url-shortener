package com.example.shortener.recorder;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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
    public void record(String code)  {
        ClickEvent event = new ClickEvent(UUID.randomUUID(), code, Instant.now());
        try {
            kafka.send(ClickTopicConfig.TOPIC, code, event).whenComplete((res, err) -> {
                if (err != null) {
                    log.debug("Click for {} code was not delivered to Kafka", code, err);
                }
            });
        } catch (RuntimeException e) {
            log.debug("Click for {} dropped, Kafka unavailable", code, e);        
        }
    }

}
