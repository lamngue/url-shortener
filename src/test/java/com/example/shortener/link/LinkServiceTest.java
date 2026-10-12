package com.example.shortener.link;

import com.example.shortener.cache.LinkCache;
import com.example.shortener.cache.LinkCacheProperties;
import com.example.shortener.config.ShortenerProperties;
import com.example.shortener.link.LinkService.OwnerCheckResult;
import com.example.shortener.shortcode.IdSource;
import com.example.shortener.shortcode.ShortCodeGenerator;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        CreateLinkResponse response = service.create("alice" ,"https://example.com/some/long/path");

        ArgumentCaptor<Link> saved = ArgumentCaptor.forClass(Link.class);
        verify(repository).save(saved.capture());
        Link link = saved.getValue();
        assertEquals(1L, link.getId());
        assertEquals("02tdk01", link.getCode());
        assertEquals("https://example.com/some/long/path", link.getLongUrl());
        assertEquals("alice", link.getOwnerSub());
        assertTrue(link.isActive());
        assertTrue(link.isNew());

        assertEquals("02tdk01", response.code());
        assertEquals("http://localhost:8080/02tdk01", response.shortUrl());
    }

    @Test
    void ownerCanDeactivate() {
        // set up: the repository "has" an active link owned by alice
        Link link = new Link(1L, "02tdk01", "https://example.com", "alice");
        when(repository.findByCode("02tdk01")).thenReturn(Optional.of(link));

        // act
        OwnerCheckResult result = service().deactivate("02tdk01", "alice");

        // check: the right result, the link changed, saved before the cache delete
        assertInstanceOf(LinkService.Owner.class, result);
        assertFalse(link.isActive());
        InOrder order = inOrder(repository, cache);
        order.verify(repository).save(link);
        order.verify(cache).evict("02tdk01");
    }

    @Test
    void nonOwnerCannotDeactivate() {
        // set up: the repository "has" an active link owned by alice
        Link link = new Link(1L, "02tdk01", "https://example.com", "alice");
        when(repository.findByCode("02tdk01")).thenReturn(Optional.of(link));

        // act
        OwnerCheckResult result = service().deactivate("02tdk01", "bob");

        // check: the right result, the link unchanged
        assertInstanceOf(LinkService.NotOwner.class, result);
        assertTrue(link.isActive());
        verify(repository, never()).save(any(Link.class));
        verify(cache, never()).evict(anyString());
    }

    @Test 
    void unknownCodeCannotDeactivate() {
        // set up: the repository "has" no link with this code
        when(repository.findByCode("02tdk01")).thenReturn(Optional.empty());

        // act
        OwnerCheckResult result = service().deactivate("02tdk01", "alice");

        // check: the right result, nothing saved or evicted
        assertInstanceOf(LinkService.NotFound.class, result);
        verify(repository, never()).save(any(Link.class));
        verify(cache, never()).evict(anyString());
    }

    // helper so each test doesn't repeat the constructor
    private LinkService service() {
        return new LinkService(idSource, generator, repository, cache,
                new ShortenerProperties("http://localhost:8080", K));
    }

    
}
