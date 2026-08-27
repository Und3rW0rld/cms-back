package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.DeleteSiteCommand;
import com.cms.domain.port.in.site.DeleteSiteUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteSiteService implements DeleteSiteUseCase {

    private final SiteRepository siteRepository;
    private final SiteOwnershipGuard siteOwnershipGuard;

    @Override
    @Transactional
    public void delete(DeleteSiteCommand command) {
        siteOwnershipGuard.requireOwnershipSite(command.siteId(), command.requesterUserId());
        log.debug("Deleting site with id {} for user {}", command.siteId(), command.requesterUserId());
        siteRepository.deleteById(command.siteId());
    }
}
