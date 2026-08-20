package com.cms.domain.port.in.site;

import com.cms.domain.model.site.SiteWithPublicationState;

/**
 * Updates site metadata (title, summary, contentSchema). Last-write-wins —
 * no optimistic locking here, unlike the draft content lifecycle (docs §4).
 * Publication state is unaffected by this operation; the returned value
 * simply reflects whatever it already was.
 */
public interface PatchSiteUseCase {

    SiteWithPublicationState patch(PatchSiteCommand command);
}
