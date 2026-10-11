package com.example.shortener.link;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;
    private final LinkStatsService linkStatsService;

    public LinkController(LinkService linkService, LinkStatsService linkStatsService) {
        this.linkService = linkService;
        this.linkStatsService = linkStatsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateLinkResponse create(@Valid @RequestBody CreateLinkRequest request) {
        return linkService.create(request.url());
    }

    // 204 also when the link was already deactivated: the caller wanted it inactive, and it is.
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String code) {
        if (!linkService.deactivate(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found");
        }
    }

    // Deactivated links answer 404 like unknown ones; their clicks stay in the database.
    @GetMapping("/{code}/stats")
    public LinkStatsResponse stats(@PathVariable String code) {
        return linkStatsService.stats(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found"));
    }
}
