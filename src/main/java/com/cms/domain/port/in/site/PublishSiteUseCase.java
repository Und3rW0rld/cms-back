package com.cms.domain.port.in.site;

import com.cms.domain.model.site.SiteWithPublicationState;

public interface PublishSiteUseCase {
    SiteWithPublicationState publishSite(PublishSiteCommand command);
}
