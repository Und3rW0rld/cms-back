package com.cms.domain.port.out;

import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;

import java.util.Optional;
import java.util.UUID;

public interface DraftSiteRepository {
    Optional<DraftSite> getDraftSiteById(UUID siteId);
    DraftSite save(DraftSite draftSite);
    DraftSite update(UUID siteId, Content content, long expectedVersion);
}
