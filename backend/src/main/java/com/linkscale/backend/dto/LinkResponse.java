package com.linkscale.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record LinkResponse(
        Long id,
        String code,
        @JsonProperty("destination_url") String destinationUrl,
        @JsonProperty("short_url") String shortUrl,
        String title,
        @JsonProperty("expires_at") OffsetDateTime expiresAt,
        boolean active,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}

