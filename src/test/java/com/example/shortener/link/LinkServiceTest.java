package com.example.shortener.link;

import com.example.shortener.cache.LinkCache;
import com.example.shortener.cache.LinkCacheProperties;
import com.example.shortener.config.ShortenerProperties;
import com.example.shortener.link.LinkService.OwnerCheckResult;
import com.example.shortener.shortcode.IdSource;
import com.example.shortener.shortcode.ShortCodeGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void testCreateTakesAnIdEncodesItAndSavesTheLink() {
        CreateLinkResponse response = service().create("https://example.com/hahaha/", "alice");
        ArgumentCaptor<Link> savedLinkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(repository).save(savedLinkCaptor.capture());
        Link savedLink = savedLinkCaptor.getValue();
        assertEquals("https://example.com/hahaha/", savedLink.getLongUrl());
        assertEquals("alice", savedLink.getOwnerSub());
        assertEquals("02tdk01", savedLink.getCode());
        assertTrue(savedLink.isActive());
        assertTrue(savedLink.isNew());

        assertEquals("02tdk01", response.code());
        assertEquals("http://localhost:8080/02tdk01", response.shortUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/f", "not a url", "https:no-host", "javascript:alert(1)" })
    void testInvalidUrlsAreRejected(String url) {
        assertThrows(ResponseStatusException.class, () -> service().create(url, "alice"));
    }

    @Test
    void testNullUrlIsRejected() {
        assertThrows(ResponseStatusException.class, () -> service().create(null, "alice"));
    }

    @Test 
    void testTooLongUrlIsRejected() {
        String longUrl = "http://example.com/" + "a".repeat(2049);
        assertThrows(ResponseStatusException.class, () -> service().create(longUrl, "alice"));
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
        order.verify(cache).invalidate("02tdk01");
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
        verify(cache, never()).invalidate(anyString());
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
        verify(cache, never()).invalidate(anyString());
    }

    // helper so each test doesn't repeat the constructor
    private LinkService service() {
        return new LinkService(idSource, generator, repository, cache,
                new ShortenerProperties("http://localhost:8080", K), new HttpUrlValidator());
    }

    
}
