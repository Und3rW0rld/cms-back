package com.cms.domain.model.site;

import java.time.Instant;
import java.util.UUID;

public record PublishedSite(UUID id, Content content, Instant publishedAt) {
}
