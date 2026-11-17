package com.linkscale.backend.domain;

import java.time.OffsetDateTime;

public record RedirectTarget(Long linkId, String destinationUrl, OffsetDateTime expiresAt) {}
