package com.cms.domain.port.out;

import com.cms.domain.model.site.PublishedSite;

import java.util.UUID;

public interface PublishedSiteRepository {
    void upsert(PublishedSite publishedSite);
    void deleteBySiteId(UUID siteId);
}
