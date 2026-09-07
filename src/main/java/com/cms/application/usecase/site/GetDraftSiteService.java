package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.port.in.site.GetDraftSiteCommand;
import com.cms.domain.port.in.site.GetDraftSiteUseCase;
import com.cms.domain.port.out.DraftSiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDraftSiteService implements GetDraftSiteUseCase {

    private final SiteOwnershipGuard siteOwnershipGuard;
    private final DraftSiteRepository draftSiteRepository;


    @Override
    public DraftSite getDraftSiteById(GetDraftSiteCommand command) {
        log.debug("Getting draft site with id {} for user {}", command.id(), command.userId());

        siteOwnershipGuard.requireOwnershipSite(command.id(), command.userId());

        return draftSiteRepository.getDraftSiteById(command.id()).orElseThrow(
            () -> new NotFoundException("Draft site not found for id: " + command.id()));
    }
}
