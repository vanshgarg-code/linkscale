package com.linkscale.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String email,
        @JsonProperty("created_at")
        OffsetDateTime createdAt
) {}

