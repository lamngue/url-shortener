package com.example.shortener.redirect;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {
    
    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/{code}")
    public ResponseEntity<String> redirect(@PathVariable String code) {
        Optional<String> longUrl = redirectService.redirect(code);
        if (longUrl.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Link not found");
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl.get()))
                .build();
        }
}
