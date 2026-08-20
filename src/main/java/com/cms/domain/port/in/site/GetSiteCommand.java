package com.cms.domain.port.in.site;

import java.util.UUID;

public record GetSiteCommand(UUID siteId, Long requesterUserId) {
}
