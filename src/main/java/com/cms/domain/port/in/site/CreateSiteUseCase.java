package com.cms.domain.port.in.site;

import com.cms.domain.model.site.Site;

public interface CreateSiteUseCase {

    Site create(CreateSiteCommand command);
}
