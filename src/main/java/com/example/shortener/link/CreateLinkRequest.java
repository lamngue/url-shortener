package com.example.shortener.link;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.net.URISyntaxException;

public record CreateLinkRequest(
        @NotBlank @Size(max = 2048) String url) {

    // Only http and https links are accepted (@URL alone would also allow ftp and others).
    @AssertTrue(message = "must be an http or https URL")
    boolean isHttpUrl() {
        if (url == null || url.isBlank()) {
            return true; // reported by @NotBlank
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
