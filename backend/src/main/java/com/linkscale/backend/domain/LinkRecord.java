package com.linkscale.backend.domain;

import java.time.OffsetDateTime;

public record LinkRecord(
        Long id,
        String code,
        String destinationUrl,
        Long ownerId,
        String title,
        OffsetDateTime expiresAt,
        boolean active,
        OffsetDateTime deletedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isAccessible(OffsetDateTime now) {
        return active && deletedAt == null && !isExpired(now);
    }
}

