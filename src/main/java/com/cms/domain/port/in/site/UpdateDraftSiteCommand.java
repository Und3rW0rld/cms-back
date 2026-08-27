package com.cms.domain.port.in.site;

import java.util.UUID;

public record UpdateDraftSiteCommand(
        UUID id,
        long userId,
        String content,
        long expectedVersion
) {
}
