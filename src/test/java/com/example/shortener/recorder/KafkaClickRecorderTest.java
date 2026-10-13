package com.example.shortener.recorder;
import static org.mockito.ArgumentMatchers.eq;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;
import java.time.Duration;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThatNoException;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class KafkaClickRecorderTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ClickEvent> kafka = mock(KafkaTemplate.class);
    private final KafkaClickRecorder recorder = new KafkaClickRecorder(kafka);


    @Test
    void sendsEventKeyedByCodeToTheClickTopic() {
        when(kafka.send(anyString(), anyString(), any(ClickEvent.class))).thenReturn(new CompletableFuture<>());
        Instant now = Instant.now();

        recorder.record("02tdk01");
        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(kafka).send(eq(ClickTopicConfig.TOPIC), eq("02tdk01"), captor.capture());
        ClickEvent event = captor.getValue();
        assertEquals("02tdk01", event.code());
        assertNotNull(event.eventId());
        assertTrue(Duration.between(now, event.clickedAt()).toMillis() < 1000);
    }

  
    @Test
    void kafkaUnreachableDoesNotFailTheRedirect() {
        when(kafka.send(anyString(), anyString(), any(ClickEvent.class)))
        .thenThrow(new TimeoutException("Kafka un   reachable after max.block.ms"));
        // The test passes if no exception is thrown
        assertThatNoException().isThrownBy(() -> recorder.record("02tdk01"));
    }
}
