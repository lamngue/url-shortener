package com.example.shortener.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
        when(values.get("link:abc123")).thenReturn("https://example.com");   // fake Redis has the URL
        LinkCache.RedisLookUpResult result = cache.get("abc123");
        assertThat(result).isEqualTo(new LinkCache.Hit("https://example.com"));
    }

    @Test 
    void testNotFoundIsStoredInRedis() {
        when(values.get("link:abc123")).thenReturn(LinkCache.NOT_FOUND);
        LinkCache.RedisLookUpResult result = cache.get("abc123");
        assertThat(result).isEqualTo(new LinkCache.KnownNotFound());
    }

    @Test 
    void testNothingIsStoredInRedis() {
        when(values.get("link:abc123")).thenReturn(null);
        LinkCache.RedisLookUpResult result = cache.get("abc123");
        assertThat(result).isEqualTo(new LinkCache.Miss());
    }

    @Test 
    void testRedisFailureOnLookUpIsAMiss() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis is down"));
        LinkCache.RedisLookUpResult result = cache.get("abc123");
        assertThat(result).isEqualTo(new LinkCache.Miss());
    }

    @Test 
    void testRedisFailOnAWrite() {
        doThrow(new RedisConnectionFailureException("down"))
        .when(values).set(anyString(), anyString(), any(Duration.class));
        assertThatNoException().isThrownBy(() -> cache.putUrl("abc123", "https://example.com"));
    }

    @Test 
    void testRedisFailOnInvalidate() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatNoException().isThrownBy(() -> cache.invalidate("abc123"));
    }
   
}
