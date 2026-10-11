package com.example.shortener;

import org.springframework.stereotype.Service;

@Service
public class LinkService {

    // Placeholder owner until authentication exists; then this becomes the token's `sub`.
    static final String ANONYMOUS_OWNER = "anonymous";

    private final IdSource idSource;
    private final ShortCodeGenerator generator;
    private final LinkRepository repository;
    private final String baseUrl;

    public LinkService(IdSource idSource, ShortCodeGenerator generator,
                       LinkRepository repository, ShortenerProperties properties) {
        this.idSource = idSource;
        this.generator = generator;
        this.repository = repository;
        this.baseUrl = stripTrailingSlash(properties.baseUrl());
    }

    // No @Transactional: the only write is one save(), which is already atomic. If it fails,
    // the id is simply lost (sequences never roll back), and gaps cost nothing.
    public CreateLinkResponse create(String longUrl) {
        long id = idSource.nextId();
        String code = generator.generate(id);
        repository.save(new Link(id, code, longUrl, ANONYMOUS_OWNER));
        return new CreateLinkResponse(code, baseUrl + "/" + code);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
