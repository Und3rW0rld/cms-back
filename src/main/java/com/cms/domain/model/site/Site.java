package com.cms.domain.model.site;

import java.time.Instant;
import java.util.UUID;

public record Site(
        UUID id,
        Long ownerUserId,
        String title,
        String summary,
        String contentSchema,
        Instant createdAt,
        Instant updatedAt
) {
}
