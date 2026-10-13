package com.example.shortener.redirect;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.shortener.cache.LinkCache;
import com.example.shortener.link.Link;
import com.example.shortener.link.LinkRepository;
import com.example.shortener.recorder.ClickRecorder;

public class RedirectServiceTest {
    private static final String CODE = "02tdk01";
    private static final String URL = "https://example.com/some/long/path";

    private final LinkCache cache = mock(LinkCache.class);
    private final LinkRepository repository = mock(LinkRepository.class);
    private final ClickRecorder clickRecorder = mock(ClickRecorder.class);
    private final RedirectService service = new RedirectService(cache, repository, clickRecorder);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"abc", "favicon.ico"})
    void testInvalidCodeNoTouchingCacheOrDatabase(String code) {
        assertThat(service.redirect(code)).isEmpty();

        verifyNoInteractions(cache, repository, clickRecorder);
    }

    @Test 
    void testCacheHit() {
        when(cache.get(CODE)).thenReturn(new LinkCache.Hit(URL));
        assertThat(service.redirect(CODE)).contains(URL);
        verifyNoInteractions(repository);
        verify(clickRecorder).record(CODE);
    }

    @Test
    void testCacheKnownNotFound() {
        when(cache.get(CODE)).thenReturn(new LinkCache.KnownNotFound());
        assertThat(service.redirect(CODE)).isEmpty();
        verifyNoInteractions(repository);
        verifyNoInteractions(clickRecorder);
    }

    @Test 
    void testCacheMissLinkInRepository() {
        when(cache.get(CODE)).thenReturn(new LinkCache.Miss());
        when(repository.findByCode(CODE)).thenReturn(Optional.of(new Link(1L, CODE, URL, "anonymous")));
        assertThat(service.redirect(CODE)).contains(URL);
        verify(cache).putUrl(CODE, URL);
        verify(clickRecorder).record(CODE);
    }

    @Test 
    void testCacheMissLinkNotInRepository() {
        when(cache.get(CODE)).thenReturn(new LinkCache.Miss());
        when(repository.findByCode(CODE)).thenReturn(Optional.empty());
        assertThat(service.redirect(CODE)).isEmpty();
        verify(cache).putNotFound(CODE);
        verifyNoInteractions(clickRecorder);
    }
}
