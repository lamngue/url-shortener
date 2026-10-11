package com.example.shortener;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LinkServiceTest {

    private static final long K = 2654435761L;

    // IdSource has one method, so a lambda is enough as a fake that always returns id 1.
    private final IdSource idSource = () -> 1L;
    private final LinkRepository repository = mock(LinkRepository.class);
    private final ShortCodeGenerator generator = new ShortCodeGenerator(K);

    @Test
    void createTakesAnIdEncodesItAndSavesTheLink() {
        LinkService service = new LinkService(idSource, generator, repository,
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
        LinkService service = new LinkService(idSource, generator, repository,
                new ShortenerProperties("https://sho.rt/", K));

        assertEquals("https://sho.rt/02tdk01", service.create("https://example.com").shortUrl());
    }
}
