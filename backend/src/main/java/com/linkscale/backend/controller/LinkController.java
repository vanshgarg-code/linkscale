package com.linkscale.backend.controller;

import com.linkscale.backend.dto.CreateLinkRequest;
import com.linkscale.backend.dto.LinkResponse;
import com.linkscale.backend.dto.PageResponse;
import com.linkscale.backend.dto.UpdateLinkRequest;
import com.linkscale.backend.service.AuthService;
import com.linkscale.backend.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;
    private final AuthService authService;

    public LinkController(LinkService linkService, AuthService authService) {
        this.linkService = linkService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> createLink(@Valid @RequestBody CreateLinkRequest request,
                                                   Authentication authentication) {
        Long ownerId = authService.getAuthenticatedUser(authentication).id();
        LinkResponse response = linkService.createLink(request, ownerId);
        return ResponseEntity.created(URI.create("/r/" + response.code())).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<LinkResponse>> listLinks(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
            Authentication authentication) {
        Long ownerId = authService.getAuthenticatedUser(authentication).id();
        String effectiveSearch = (search != null && !search.isBlank()) ? search : q;
        PageResponse<LinkResponse> response = linkService.listLinks(ownerId, effectiveSearch, status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LinkResponse> getLink(@PathVariable("id") Long id,
                                               Authentication authentication) {
        Long ownerId = authService.getAuthenticatedUser(authentication).id();
        LinkResponse response = linkService.getLink(id, ownerId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<LinkResponse> updateLink(@PathVariable("id") Long id,
                                                  @Valid @RequestBody UpdateLinkRequest request,
                                                  Authentication authentication) {
        Long ownerId = authService.getAuthenticatedUser(authentication).id();
        LinkResponse response = linkService.updateLink(id, ownerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLink(@PathVariable("id") Long id,
                                          Authentication authentication) {
        Long ownerId = authService.getAuthenticatedUser(authentication).id();
        linkService.deleteLink(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}
