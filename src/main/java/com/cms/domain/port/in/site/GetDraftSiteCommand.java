package com.cms.domain.port.in.site;

import java.util.UUID;

public record GetDraftSiteCommand(
        UUID id,
        long userId
) {
}
