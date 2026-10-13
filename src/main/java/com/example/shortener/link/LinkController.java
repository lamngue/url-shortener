package com.example.shortener.link;

import jakarta.validation.Valid;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public CreateLinkResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateLinkRequest request) {
        String callerSub = jwt.getSubject();
        return linkService.create(callerSub, request.url());
    }

    // 204 also when the link was already deactivated: the caller wanted it inactive, and it is.
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        String callerSub = jwt.getSubject();
        switch (linkService.deactivate(code, callerSub)) {
            case LinkService.Owner owner -> { }                       // 204: nothing to do
            case LinkService.NotOwner notOwner -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this link");
            case LinkService.NotFound notFound -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found");
        }
    } 

    // Deactivated links answer 404 like unknown ones; their clicks stay in the database.
    @GetMapping("/{code}/stats")
    public LinkStatsResponse stats(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        String callerSub = jwt.getSubject();
        switch (linkStatsService.stats(code, callerSub)) {
            case LinkStatsService.Owner stats -> { return stats.stats(); }
            case LinkStatsService.NotOwner notOwner -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this link");
            case LinkStatsService.NotFound notFound -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found");
        }   
    }
}
