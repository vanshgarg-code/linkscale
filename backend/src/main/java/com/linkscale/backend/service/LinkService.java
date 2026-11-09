package com.linkscale.backend.service;

import com.linkscale.backend.config.LinkScaleProperties;
import com.linkscale.backend.domain.LinkRecord;
import com.linkscale.backend.dto.CreateLinkRequest;
import com.linkscale.backend.dto.LinkResponse;
import com.linkscale.backend.exception.CodeGenerationExhaustedException;
import com.linkscale.backend.exception.CustomAliasConflictException;
import com.linkscale.backend.exception.LinkExpiredException;
import com.linkscale.backend.exception.LinkNotFoundException;
import com.linkscale.backend.repository.LinkRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final DevFixtureService devFixtureService;
    private final UrlValidator urlValidator;
    private final CustomAliasValidator customAliasValidator;
    private final CodeGenerator codeGenerator;
    private final LinkScaleProperties properties;

    public LinkService(LinkRepository linkRepository,
                       DevFixtureService devFixtureService,
                       UrlValidator urlValidator,
                       CustomAliasValidator customAliasValidator,
                       CodeGenerator codeGenerator,
                       LinkScaleProperties properties) {
        this.linkRepository = linkRepository;
        this.devFixtureService = devFixtureService;
        this.urlValidator = urlValidator;
        this.customAliasValidator = customAliasValidator;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
    }

    public LinkResponse createLink(CreateLinkRequest request) {
        String destinationUrl = urlValidator.validateAndNormalize(request.destinationUrl());
        Long ownerId = devFixtureService.getOrCreateDevFixtureUserId();

        String rawAlias = request.customAlias();
        if (rawAlias != null && !rawAlias.isBlank()) {
            return createWithCustomAlias(destinationUrl, ownerId, request.title(), request.expiresAt(), rawAlias);
        } else {
            return createWithGeneratedCode(destinationUrl, ownerId, request.title(), request.expiresAt());
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

    public String resolveDestinationUrl(String rawCode) {
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

        return link.destinationUrl();
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
                link.createdAt()
        );
    }
}

