package com.cms.domain.port.in.site;

import java.util.UUID;

public record DeleteSiteCommand(UUID siteId, Long requesterUserId) {
}
