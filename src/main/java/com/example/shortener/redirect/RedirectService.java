package com.example.shortener.redirect;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.shortener.cache.LinkCache;
import com.example.shortener.cache.LinkCache.RedisLookUpResult;
import com.example.shortener.link.LinkRepository;
import com.example.shortener.recorder.ClickRecorder;
import com.example.shortener.shortcode.ShortCodeGenerator;

@Service
public class RedirectService {
    private final LinkCache linkCache;
    private final LinkRepository linkRepository;
    private final ClickRecorder clickRecorder;

    public RedirectService(LinkCache linkCache, LinkRepository linkRepository, ClickRecorder clickRecorder) {
        this.linkCache = linkCache;
        this.linkRepository = linkRepository;
        this.clickRecorder = clickRecorder;
    }

    public Optional<String> redirect(String code) {
        if (!ShortCodeGenerator.isValidCode(code)) {
            return Optional.empty();
        }
        // the cache
        RedisLookUpResult cachedUrl = linkCache.get(code);
        Optional<String> longUrl;

        if (cachedUrl instanceof LinkCache.Hit hit) {
            longUrl = Optional.of(hit.longUrl());
        } else if (cachedUrl instanceof LinkCache.KnownNotFound) {
            longUrl = Optional.empty();
        } else if (cachedUrl instanceof LinkCache.Miss) {
            longUrl = loadFromDatabase(code);
        } else {
            throw new IllegalStateException("Unexpected cache result: " + cachedUrl);
        }
        longUrl.ifPresent(url -> clickRecorder.record(code));
        return longUrl;
    }

    private Optional<String> loadFromDatabase(String code) {
        Optional<String> longUrl = linkRepository.findByCode(code)
                .filter(link -> link.isActive())
                .map(link -> link.getLongUrl());

        if (longUrl.isPresent()) {
            linkCache.putUrl(code, longUrl.get());
        } else {
            linkCache.putNotFound(code);
        }
        return longUrl;
    }
}
