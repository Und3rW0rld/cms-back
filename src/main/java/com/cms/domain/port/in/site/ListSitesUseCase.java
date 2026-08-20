package com.cms.domain.port.in.site;

import com.cms.domain.model.site.SiteWithPublicationState;

import java.util.List;

/**
 * Lists every site owned by the requester, including each site's publication
 * state — computed without a per-site follow-up query (docs §7).
 */
public interface ListSitesUseCase {

    List<SiteWithPublicationState> listByOwner(Long ownerUserId);
}
