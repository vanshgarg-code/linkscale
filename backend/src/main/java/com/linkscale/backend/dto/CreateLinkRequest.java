package com.linkscale.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateLinkRequest(
        @NotBlank(message = "destination_url must not be blank")
        @JsonProperty("destination_url")
        String destinationUrl,

        @JsonProperty("custom_alias")
        String customAlias,

        @Size(max = 255, message = "title must not exceed 255 characters")
        String title,

        @JsonProperty("expires_at")
        OffsetDateTime expiresAt
) {}

