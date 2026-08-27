package com.cms.domain.port.in.site;

import com.cms.domain.model.site.DraftSite;

public interface UpdateDraftSiteUseCase {
    DraftSite updateDraftSite(UpdateDraftSiteCommand command);
}
