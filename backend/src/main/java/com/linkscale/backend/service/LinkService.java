package com.linkscale.backend.service;

import com.linkscale.backend.config.LinkScaleProperties;
import com.linkscale.backend.domain.LinkRecord;
import com.linkscale.backend.dto.CreateLinkRequest;
import com.linkscale.backend.dto.LinkResponse;
import com.linkscale.backend.dto.PageResponse;
import com.linkscale.backend.dto.UpdateLinkRequest;
import com.linkscale.backend.exception.CodeGenerationExhaustedException;
import com.linkscale.backend.exception.CustomAliasConflictException;
import com.linkscale.backend.exception.LinkExpiredException;
import com.linkscale.backend.exception.LinkNotFoundException;
import com.linkscale.backend.repository.LinkRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final UrlValidator urlValidator;
    private final CustomAliasValidator customAliasValidator;
    private final CodeGenerator codeGenerator;
    private final LinkScaleProperties properties;

    public LinkService(LinkRepository linkRepository,
                       UrlValidator urlValidator,
                       CustomAliasValidator customAliasValidator,
                       CodeGenerator codeGenerator,
                       LinkScaleProperties properties) {
        this.linkRepository = linkRepository;
        this.urlValidator = urlValidator;
        this.customAliasValidator = customAliasValidator;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
    }

    public LinkResponse createLink(CreateLinkRequest request, Long ownerId) {
        String destinationUrl = urlValidator.validateAndNormalize(request.destinationUrl());

        String rawAlias = request.customAlias();
        if (rawAlias != null && !rawAlias.isBlank()) {
            return createWithCustomAlias(destinationUrl, ownerId, request.title(), request.expiresAt(), rawAlias);
        } else {
            return createWithGeneratedCode(destinationUrl, ownerId, request.title(), request.expiresAt());
        }
    }

    public LinkResponse getLink(Long id, Long ownerId) {
        LinkRecord link = linkRepository.findByIdAndOwner(id, ownerId)
                .orElseThrow(() -> new LinkNotFoundException("Link not found: " + id));

        return toResponse(link);
    }

    public PageResponse<LinkResponse> listLinks(Long ownerId, String search, String status, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (status != null && !status.isBlank()) {
            String s = status.trim().toLowerCase(Locale.ROOT);
            if (!s.equals("all") && !s.equals("active") && !s.equals("inactive") && !s.equals("expired")) {
                throw new IllegalArgumentException("Invalid status filter: '" + status + "'. Allowed values: all, active, inactive, expired");
            }
        }

        List<LinkRecord> records = linkRepository.findByOwnerWithFilters(ownerId, search, status, page, size);
        long totalElements = linkRepository.countByOwnerWithFilters(ownerId, search, status);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        List<LinkResponse> content = records.stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    public LinkResponse updateLink(Long id, Long ownerId, UpdateLinkRequest request) {
        if (request.isTitleSet() && request.getTitle() != null && request.getTitle().length() > 255) {
            throw new IllegalArgumentException("title must not exceed 255 characters");
        }
        if (request.isActiveSet() && request.getActive() == null) {
            throw new IllegalArgumentException("active status must not be null when provided");
        }

        LinkRecord updated = linkRepository.updateLink(
                id,
                ownerId,
                request.isTitleSet(),
                request.getTitle(),
                request.isExpiresAtSet(),
                request.getExpiresAt(),
                request.isActiveSet(),
                request.getActive()
        ).orElseThrow(() -> new LinkNotFoundException("Link not found: " + id));

        return toResponse(updated);
    }

    public void deleteLink(Long id, Long ownerId) {
        boolean deleted = linkRepository.softDelete(id, ownerId);
        if (!deleted) {
            throw new LinkNotFoundException("Link not found: " + id);
        }
    }

    private LinkResponse createWithCustomAlias(String destinationUrl,
                                               Long ownerId,
                                               String title,
                                               OffsetDateTime expiresAt,
                                               String rawAlias) {
        String alias = customAliasValidator.validateAndNormalize(rawAlias);

        Optional<LinkRecord> inserted = linkRepository.insertOnConflictDoNothing(
                alias, destinationUrl, ownerId, title, expiresAt
        );

        if (inserted.isEmpty()) {
            throw new CustomAliasConflictException("Custom alias '" + alias + "' is already in use");
        }

        return toResponse(inserted.get());
    }

    private LinkResponse createWithGeneratedCode(String destinationUrl,
                                                 Long ownerId,
                                                 String title,
                                                 OffsetDateTime expiresAt) {
        int maxRetries = properties.generator() != null ? properties.generator().maxRetries() : 5;
        int codeLength = properties.generator() != null ? properties.generator().codeLength() : 8;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String code = codeGenerator.generateCode(codeLength);
            Optional<LinkRecord> inserted = linkRepository.insertOnConflictDoNothing(
                    code, destinationUrl, ownerId, title, expiresAt
            );

            if (inserted.isPresent()) {
                return toResponse(inserted.get());
            }
        }

        throw new CodeGenerationExhaustedException(
                "Code generation service temporarily unavailable after exhausting retry attempts"
        );
    }

    public LinkRecord resolveRedirect(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new LinkNotFoundException("Link code must not be blank");
        }

        String code = rawCode.trim().toLowerCase(Locale.ROOT);
        LinkRecord link = linkRepository.findByCode(code)
                .orElseThrow(() -> new LinkNotFoundException("Link not found: " + code));

        if (!link.active() || link.deletedAt() != null) {
            throw new LinkNotFoundException("Link is inactive or deleted: " + code);
        }

        if (link.isExpired(OffsetDateTime.now())) {
            throw new LinkExpiredException("Link has expired: " + code);
        }

        return link;
    }

    private LinkResponse toResponse(LinkRecord link) {
        String shortUrl = properties.baseUrl() + "/r/" + link.code();
        return new LinkResponse(
                link.id(),
                link.code(),
                link.destinationUrl(),
                shortUrl,
                link.title(),
                link.expiresAt(),
                link.active(),
                link.createdAt(),
                link.updatedAt()
        );
    }
}
