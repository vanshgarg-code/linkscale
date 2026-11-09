package com.linkscale.backend.controller;

import com.linkscale.backend.service.LinkService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable("code") String code) {
        String destinationUrl = linkService.resolveDestinationUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destinationUrl))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}

