package com.cms.domain.model.site;

import java.time.Instant;
import java.util.UUID;

public record DraftSite(
        UUID id,
        long version,
        Content content,
        Instant updatedAt
) {
}
