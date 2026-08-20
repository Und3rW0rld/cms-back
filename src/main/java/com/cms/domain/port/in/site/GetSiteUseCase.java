package com.cms.domain.port.in.site;

import com.cms.domain.model.site.SiteWithPublicationState;

/**
 * Reads a single site owned by the requester, including its publication
 * state (row existence in site_published — docs §2). Ownership is validated
 * here, not left to authentication alone (AGENTS.md rule).
 */
public interface GetSiteUseCase {

    SiteWithPublicationState getById(GetSiteCommand command);
}
