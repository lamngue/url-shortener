package com.example.shortener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis cache for code -> long URL (cache-aside). Redis is only a performance layer: every
 * Redis failure is treated as a miss or ignored, so it can never fail a request.
 */
@Component
public class LinkCache {

    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);

    private static final String KEY_PREFIX = "link:";
    // Stored for unknown or inactive codes. Never a real value: create only accepts http/https URLs.
    static final String NOT_FOUND = "NOT_FOUND";

    /** The three outcomes of a lookup. */
    public sealed interface Lookup permits Hit, KnownNotFound, Miss {
    }

    /** Redis has the long URL. */
    public record Hit(String longUrl) implements Lookup {
    }

    /** Redis remembers that this code has no active link. */
    public record KnownNotFound() implements Lookup {
    }

    /** Redis has nothing usable (no entry, or Redis failed): ask Postgres. */
    public record Miss() implements Lookup {
    }

    private final StringRedisTemplate redis;
    private final LinkCacheProperties properties;

    public LinkCache(StringRedisTemplate redis, LinkCacheProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Lookup lookup(String code) {
        String value;
        try {
            value = redis.opsForValue().get(key(code));
        } catch (RuntimeException e) {
            // Logged at DEBUG: while Redis is down this happens on every redirect.
            log.debug("Redis lookup failed for {}, falling back to the database", code, e);
            return new Miss();
        }
        if (value == null) {
            return new Miss();
        }
        return NOT_FOUND.equals(value) ? new KnownNotFound() : new Hit(value);
    }

    public void putUrl(String code, String longUrl) {
        set(code, longUrl, positiveTtlWithJitter());
    }

    public void putNotFound(String code) {
        set(code, NOT_FOUND, properties.negativeTtl());
    }

    public void evict(String code) {
        try {
            redis.delete(key(code));
        } catch (RuntimeException e) {
            // The entry then lives until its TTL expires, which is the bound we accepted.
            log.debug("Redis evict failed for {}", code, e);
        }
    }

    private void set(String code, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key(code), value, ttl);
        } catch (RuntimeException e) {
            log.debug("Redis write failed for {}", code, e);
        }
    }

    // positiveTtl plus a random 0..jitter share, so entries created together don't expire together.
    Duration positiveTtlWithJitter() {
        long base = properties.positiveTtl().toMillis();
        double extra = ThreadLocalRandom.current().nextDouble() * properties.jitter();
        return Duration.ofMillis(base + (long) (base * extra));
    }

    private static String key(String code) {
        return KEY_PREFIX + code;
    }
}
