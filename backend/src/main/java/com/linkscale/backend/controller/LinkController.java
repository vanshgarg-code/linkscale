package com.linkscale.backend.controller;

import com.linkscale.backend.dto.CreateLinkRequest;
import com.linkscale.backend.dto.LinkResponse;
import com.linkscale.backend.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> createLink(@Valid @RequestBody CreateLinkRequest request) {
        LinkResponse response = linkService.createLink(request);
        return ResponseEntity.created(URI.create("/r/" + response.code())).body(response);
    }
}

