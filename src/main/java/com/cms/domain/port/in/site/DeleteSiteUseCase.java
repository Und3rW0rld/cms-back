package com.cms.domain.port.in.site;

/**
 * Deletes a site owned by the requester, along with all its dependent
 * content (entries, drafts, published snapshots).
 */
public interface DeleteSiteUseCase {

    void delete(DeleteSiteCommand command);
}
