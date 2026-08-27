package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.GetSiteCommand;
import com.cms.domain.port.in.site.GetSiteUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetSiteService implements GetSiteUseCase {

    private final SiteOwnershipGuard siteOwnershipGuard;

    @Override
    public SiteWithPublicationState getById(GetSiteCommand command) {
        log.debug("Getting site with id {} for user {}", command.siteId(), command.requesterUserId());
        return siteOwnershipGuard.requireOwnershipSiteWithPublicationState(command.siteId(), command.requesterUserId());
    }
}
