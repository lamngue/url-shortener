package com.example.shortener.link;

import com.example.shortener.cache.LinkCache;
import com.example.shortener.config.ShortenerProperties;
import com.example.shortener.shortcode.IdSource;
import com.example.shortener.shortcode.ShortCodeGenerator;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LinkService {

    // Placeholder owner until authentication exists; then this becomes the token's `sub`.
    static final String ANONYMOUS_OWNER = "anonymous";

    private final IdSource idSource;
    private final ShortCodeGenerator generator;
    private final LinkRepository repository;
    private final LinkCache cache;
    private final String baseUrl;

    public LinkService(IdSource idSource, ShortCodeGenerator generator, LinkRepository repository,
                       LinkCache cache, ShortenerProperties properties) {
        this.idSource = idSource;
        this.generator = generator;
        this.repository = repository;
        this.cache = cache;
        this.baseUrl = stripTrailingSlash(properties.baseUrl());
    }

    // No @Transactional: the only write is one save(), which is already atomic. If it fails,
    // the id is simply lost (sequences never roll back), and gaps cost nothing.
    public CreateLinkResponse create(String longUrl) {
        long id = idSource.nextId();
        String code = generator.generate(id);
        repository.save(new Link(id, code, longUrl, ANONYMOUS_OWNER));
        // After the save, like D-03: someone may have probed this code before it existed, leaving
        // a NOT_FOUND entry. LinkCache ignores Redis errors, so this cannot fail the create.
        cache.evict(code);
        return new CreateLinkResponse(code, baseUrl + "/" + code);
    }

    /**
     * Deactivates a link. Returns false if no link has this code. Deactivating an already
     * inactive link is not an error (DELETE is idempotent).
     */
    public boolean deactivate(String code) {
        if (!ShortCodeGenerator.isValidCode(code)) {
            return false;
        }
        Optional<Link> found = repository.findByCode(code);
        if (found.isEmpty()) {
            return false;
        }
        Link link = found.get();
        if (link.isActive()) {
            link.deactivate();
            repository.save(link);
        }
        // Database first, then cache (D-03). A redirect that read the old row just before the
        // update can still put it back; the cache TTL limits how long that lasts. Evicting even
        // when the link was already inactive also clears a stale entry left by an earlier failed
        // evict.
        cache.evict(code);
        return true;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
