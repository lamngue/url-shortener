package com.example.shortener.redirect;

import com.example.shortener.cache.LinkCache;
import com.example.shortener.link.Link;
import com.example.shortener.link.LinkRepository;
import com.example.shortener.recorder.ClickRecorder;
import com.example.shortener.shortcode.ShortCodeGenerator;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves a short code to its long URL: format check, then Redis, then Postgres on a miss
 * (cache-aside). Unknown and deactivated codes are both "not found". A click is recorded only
 * when the link is found (302), never for a 404.
 */
@Service
public class RedirectService {

    private final LinkCache cache;
    private final LinkRepository repository;
    private final ClickRecorder clickRecorder;

    public RedirectService(LinkCache cache, LinkRepository repository, ClickRecorder clickRecorder) {
        this.cache = cache;
        this.repository = repository;
        this.clickRecorder = clickRecorder;
    }

    public Optional<String> resolve(String code) {
        // Strings that can never be codes stop here, before touching Redis or Postgres.
        if (!ShortCodeGenerator.isValidCode(code)) {
            return Optional.empty();
        }
        Optional<String> longUrl = switch (cache.get(code)) {
            case LinkCache.Hit hit -> Optional.of(hit.longUrl());
            case LinkCache.KnownNotFound notFound -> Optional.empty();
            case LinkCache.Miss miss -> loadAndCache(code);
        };
        longUrl.ifPresent(url -> clickRecorder.record(code));
        return longUrl;
    }

    private Optional<String> loadAndCache(String code) {
        Optional<String> longUrl = repository.findByCode(code)
                .filter(Link::isActive)
                .map(Link::getLongUrl);
        longUrl.ifPresentOrElse(
                url -> cache.putUrl(code, url),
                () -> cache.putNotFound(code));
        return longUrl;
    }
}
