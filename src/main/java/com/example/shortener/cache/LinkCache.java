package com.example.shortener.cache;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LinkCache {
    private static final Logger log = LoggerFactory.getLogger(LinkCache.class);

    private static final String LINK_CACHE_PREFIX = "link:";


    static final String NOT_FOUND = "Known not to exist";

    public sealed interface RedisLookUpResult permits Hit, Miss, KnownNotFound {
    }

    private final StringRedisTemplate redisTemplate;

    private final LinkCacheProperties properties;

    public record Hit(String longUrl) implements RedisLookUpResult {
    }

    public record Miss() implements RedisLookUpResult {
    }

    public record KnownNotFound() implements RedisLookUpResult {
    }

    public LinkCache(StringRedisTemplate redisTemplate, LinkCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public RedisLookUpResult get(String code) {
        try {
            String value = redisTemplate.opsForValue().get(key(code));
            if (value == null) {
                return new Miss();
            } else if (value.equals(NOT_FOUND)) {
                return new KnownNotFound();
            } else {
                return new Hit(value);
            }
        } catch (RuntimeException e) {
            log.debug("Redis read failed for {}", code, e);
            return new Miss();
        }
    }

    public void putUrl(String code, String url) {
        Duration ttl = positiveTtlWithJitter();
        set(code, url, ttl);
        log.debug("Cached URL for code: {} with TTL: {}", code, ttl);
    }

    public void putNotFound(String code) {
        Duration ttl = properties.negativeTtl();
        set(code, NOT_FOUND, ttl);
        log.debug("Cached NOT_FOUND for code: {} with TTL: {}", code, ttl);
    }

    public void invalidate(String code) {
        try {
            redisTemplate.delete(key(code));
            log.debug("Invalidated cache for code: {}", code);
        } catch (RuntimeException e) {
            log.debug("Redis delete failed for {}", code, e);
        }
    }

     private void set(String code, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(code), value, ttl);
        } catch (RuntimeException e) {
            log.debug("Redis write failed for {}", code, e);
        }
    }

    private String key(String code) {
        return LINK_CACHE_PREFIX + code;
    }

    Duration positiveTtlWithJitter() {
        double jitterFactor = 1.0 + (Math.random() * properties.jitter());
        return properties.positiveTtl().multipliedBy((long) (jitterFactor * 1000)).dividedBy(1000);
    }
    
}


