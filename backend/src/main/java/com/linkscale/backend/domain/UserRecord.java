package com.linkscale.backend.domain;

import java.time.OffsetDateTime;

public record UserRecord(
        Long id,
        String email,
        String passwordHash,
        OffsetDateTime createdAt
) {}

