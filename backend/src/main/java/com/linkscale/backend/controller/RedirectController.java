package com.linkscale.backend.controller;

import com.linkscale.backend.domain.LinkRecord;
import com.linkscale.backend.service.ClickTrackingService;
import com.linkscale.backend.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
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
    private final ClickTrackingService clickTrackingService;

    public RedirectController(LinkService linkService, ClickTrackingService clickTrackingService) {
        this.linkService = linkService;
        this.clickTrackingService = clickTrackingService;
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable("code") String code, HttpServletRequest request) {
        LinkRecord link = linkService.resolveRedirect(code);
        URI destination = URI.create(link.destinationUrl());
        if (HttpMethod.GET.matches(request.getMethod())) {
            clickTrackingService.recordClick(link.id());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(destination)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}

