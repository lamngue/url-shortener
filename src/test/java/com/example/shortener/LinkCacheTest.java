package com.example.shortener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkCacheTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final LinkCache cache = new LinkCache(redis,
            new LinkCacheProperties(Duration.ofMinutes(5), Duration.ofSeconds(10), 0.1));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void storedUrlIsAHit() {
        when(values.get("link:02tdk01")).thenReturn("https://example.com");

        assertThat(cache.lookup("02tdk01")).isEqualTo(new LinkCache.Hit("https://example.com"));
    }

    @Test
    void markerIsKnownNotFound() {
        when(values.get("link:02tdk01")).thenReturn(LinkCache.NOT_FOUND);

        assertThat(cache.lookup("02tdk01")).isInstanceOf(LinkCache.KnownNotFound.class);
    }

    @Test
    void noEntryIsAMiss() {
        when(values.get("link:02tdk01")).thenReturn(null);

        assertThat(cache.lookup("02tdk01")).isInstanceOf(LinkCache.Miss.class);
    }

    @Test
    void redisFailureOnLookupIsAMiss() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.lookup("02tdk01")).isInstanceOf(LinkCache.Miss.class);
    }

    @Test
    void putUrlUsesPositiveTtlWithinJitterRange() {
        cache.putUrl("02tdk01", "https://example.com");

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq("link:02tdk01"), eq("https://example.com"), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(300), Duration.ofSeconds(330));
    }

    @Test
    void putNotFoundStoresMarkerWithNegativeTtl() {
        cache.putNotFound("02tdk01");

        verify(values).set("link:02tdk01", LinkCache.NOT_FOUND, Duration.ofSeconds(10));
    }

    @Test
    void redisFailureOnWriteIsIgnored() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(values).set(anyString(), anyString(), any(Duration.class));

        assertThatNoException().isThrownBy(() -> cache.putUrl("02tdk01", "https://example.com"));
        assertThatNoException().isThrownBy(() -> cache.putNotFound("02tdk01"));
    }

    @Test
    void evictDeletesTheKeyAndIgnoresRedisFailure() {
        cache.evict("02tdk01");
        verify(redis).delete("link:02tdk01");

        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatNoException().isThrownBy(() -> cache.evict("02tdk01"));
    }

    @Test
    void jitterStaysWithinRangeOverManyDraws() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(cache.positiveTtlWithJitter()).isBetween(Duration.ofSeconds(300), Duration.ofSeconds(330));
        }
    }
}
