package com.example.shortener.redirect;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    // 302, not 301: browsers do not cache it, so every click reaches us (statistics, deactivation).
    @GetMapping("/{code}")
    public ResponseEntity<String> redirect(@PathVariable String code) {
        return redirectService.resolve(code)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).<String>build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Link not found"));
    }
}
