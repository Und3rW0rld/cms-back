package com.cms.domain.port.in.site;

import java.util.UUID;

public record PublishSiteCommand(UUID siteId, long userId) {
}
