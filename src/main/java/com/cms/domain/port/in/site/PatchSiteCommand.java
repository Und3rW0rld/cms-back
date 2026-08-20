package com.cms.domain.port.in.site;

import java.util.UUID;

public record PatchSiteCommand(UUID siteId, Long requesterUserId, String title, String summary, String contentSchema) {
}
