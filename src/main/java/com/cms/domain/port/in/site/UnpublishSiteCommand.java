package com.cms.domain.port.in.site;

import java.util.UUID;

public record UnpublishSiteCommand(UUID siteId, long userId) {
}
