package com.example.shortener.link;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.URISyntaxException;

@Component
public class HttpUrlValidator {

    public URI validate(String url) {
        try {
    
            //the validator also rejects null or blank and more than 2048 characters;
            if (url == null || url.isBlank() || url.length() > 2048) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "URL must not be null, blank, or exceed 2048 characters");
            }

            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
               throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "URL must be an HTTP or HTTPS URL");
            }

            return uri;
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "URL is not a valid URI"
            );
        }
    }
}