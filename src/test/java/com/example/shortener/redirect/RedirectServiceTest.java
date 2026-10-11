package com.example.shortener.redirect;

import com.example.shortener.cache.LinkCache;
import com.example.shortener.link.Link;
import com.example.shortener.link.LinkRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedirectServiceTest {

    private static final String CODE = "02tdk01";
    private static final String URL = "https://example.com/some/long/path";

    private final LinkCache cache = mock(LinkCache.class);
    private final LinkRepository repository = mock(LinkRepository.class);
    private final RedirectService service = new RedirectService(cache, repository);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "favicon.ico", "abc-123", "abcdefgh"})
    void invalidFormatIsNotFoundWithoutTouchingCacheOrDatabase(String code) {
        assertThat(service.resolve(code)).isEmpty();

        verifyNoInteractions(cache, repository);
    }

    @Test
    void cacheHitReturnsUrlWithoutTheDatabase() {
        when(cache.lookup(CODE)).thenReturn(new LinkCache.Hit(URL));

        assertThat(service.resolve(CODE)).contains(URL);
        verifyNoInteractions(repository);
    }

    @Test
    void cachedNotFoundReturnsEmptyWithoutTheDatabase() {
        when(cache.lookup(CODE)).thenReturn(new LinkCache.KnownNotFound());

        assertThat(service.resolve(CODE)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void missWithActiveLinkReturnsUrlAndCachesIt() {
        when(cache.lookup(CODE)).thenReturn(new LinkCache.Miss());
        when(repository.findByCode(CODE)).thenReturn(Optional.of(new Link(1L, CODE, URL, "anonymous")));

        assertThat(service.resolve(CODE)).contains(URL);
        verify(cache).putUrl(CODE, URL);
    }

    @Test
    void missWithUnknownCodeCachesNotFound() {
        when(cache.lookup(CODE)).thenReturn(new LinkCache.Miss());
        when(repository.findByCode(CODE)).thenReturn(Optional.empty());

        assertThat(service.resolve(CODE)).isEmpty();
        verify(cache).putNotFound(CODE);
    }

    @Test
    void missWithDeactivatedLinkCachesNotFound() {
        Link deactivated = mock(Link.class);
        when(deactivated.isActive()).thenReturn(false);
        when(cache.lookup(CODE)).thenReturn(new LinkCache.Miss());
        when(repository.findByCode(CODE)).thenReturn(Optional.of(deactivated));

        assertThat(service.resolve(CODE)).isEmpty();
        verify(cache).putNotFound(CODE);
    }
}
