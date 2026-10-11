package com.example.shortener.recorder;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaClickRecorderTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ClickEvent> kafka = mock(KafkaTemplate.class);
    private final KafkaClickRecorder recorder = new KafkaClickRecorder(kafka);

    @Test
    void sendsEventKeyedByCodeToTheClickTopic() {
        when(kafka.send(anyString(), anyString(), any(ClickEvent.class))).thenReturn(new CompletableFuture<>());
        Instant before = Instant.now();

        recorder.record("02tdk01");

        ArgumentCaptor<ClickEvent> event = ArgumentCaptor.forClass(ClickEvent.class);
        verify(kafka).send(eq(ClickTopicConfig.TOPIC), eq("02tdk01"), event.capture());
        assertThat(event.getValue().code()).isEqualTo("02tdk01");
        assertThat(event.getValue().eventId()).isNotNull();
        assertThat(event.getValue().clickedAt()).isBetween(before, Instant.now().plus(Duration.ofSeconds(1)));
    }

    @Test
    void eachClickGetsItsOwnEventId() {
        when(kafka.send(anyString(), anyString(), any(ClickEvent.class))).thenReturn(new CompletableFuture<>());

        recorder.record("02tdk01");
        recorder.record("02tdk01");

        ArgumentCaptor<ClickEvent> events = ArgumentCaptor.forClass(ClickEvent.class);
        verify(kafka, times(2)).send(anyString(), anyString(), events.capture());
        assertThat(events.getAllValues().get(0).eventId()).isNotEqualTo(events.getAllValues().get(1).eventId());
    }

    @Test
    void kafkaUnreachableDoesNotFailTheRedirect() {
        // What the producer throws when it cannot get metadata within max.block.ms.
        when(kafka.send(anyString(), anyString(), any(ClickEvent.class)))
                .thenThrow(new TimeoutException("Topic link-clicks not present in metadata after 100 ms."));

        assertThatNoException().isThrownBy(() -> recorder.record("02tdk01"));
    }

    @Test
    void failedDeliveryLaterDoesNotFailTheRedirect() {
        CompletableFuture<SendResult<String, ClickEvent>> failed = new CompletableFuture<>();
        when(kafka.send(anyString(), anyString(), any(ClickEvent.class))).thenReturn(failed);

        assertThatNoException().isThrownBy(() -> recorder.record("02tdk01"));
        assertThatNoException().isThrownBy(() -> failed.completeExceptionally(new RuntimeException("broker down")));
    }
}
