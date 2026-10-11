package com.example.shortener;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkServiceTest {

    private static final long K = 2654435761L;

    // IdSource has one method, so a lambda is enough as a fake that always returns id 1.
    private final IdSource idSource = () -> 1L;
    private final LinkRepository repository = mock(LinkRepository.class);
    private final ShortCodeGenerator generator = new ShortCodeGenerator(K);
    private final LinkCache cache = mock(LinkCache.class);

    @Test
    void createTakesAnIdEncodesItAndSavesTheLink() {
        LinkService service = new LinkService(idSource, generator, repository, cache,
                new ShortenerProperties("http://localhost:8080", K));

        CreateLinkResponse response = service.create("https://example.com/some/long/path");

        ArgumentCaptor<Link> saved = ArgumentCaptor.forClass(Link.class);
        verify(repository).save(saved.capture());
        Link link = saved.getValue();
        assertEquals(1L, link.getId());
        assertEquals("02tdk01", link.getCode());
        assertEquals("https://example.com/some/long/path", link.getLongUrl());
        assertEquals(LinkService.ANONYMOUS_OWNER, link.getOwnerSub());
        assertTrue(link.isActive());
        assertTrue(link.isNew());

        assertEquals("02tdk01", response.code());
        assertEquals("http://localhost:8080/02tdk01", response.shortUrl());
    }

    @Test
    void trailingSlashInBaseUrlIsNotDoubled() {
        LinkService service = new LinkService(idSource, generator, repository, cache,
                new ShortenerProperties("https://sho.rt/", K));

        assertEquals("https://sho.rt/02tdk01", service.create("https://example.com").shortUrl());
    }

    @Test
    void evictsTheCodeAfterSaving() {
        LinkService service = new LinkService(idSource, generator, repository, cache,
                new ShortenerProperties("http://localhost:8080", K));

        service.create("https://example.com");

        // Order matters (D-03): evicting before the save could let a reader re-cache NOT_FOUND.
        InOrder order = inOrder(repository, cache);
        order.verify(repository).save(any(Link.class));
        order.verify(cache).evict("02tdk01");
    }

    @Test
    void redisDownDoesNotFailTheCreate() {
        // A real LinkCache over a Redis that always fails, to show the create still succeeds.
        StringRedisTemplate brokenRedis = mock(StringRedisTemplate.class);
        when(brokenRedis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        LinkCache realCache = new LinkCache(brokenRedis,
                new LinkCacheProperties(Duration.ofMinutes(5), Duration.ofSeconds(10), 0.1));
        LinkService service = new LinkService(idSource, generator, repository, realCache,
                new ShortenerProperties("http://localhost:8080", K));

        CreateLinkResponse response = service.create("https://example.com");

        assertEquals("02tdk01", response.code());
        verify(repository).save(any(Link.class));
    }
}
