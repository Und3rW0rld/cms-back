package com.cms.application.usecase.site;

import com.cms.domain.port.in.site.UnpublishSiteCommand;
import com.cms.domain.port.in.site.UnpublishSiteUseCase;
import com.cms.domain.port.out.PublishedSiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnpublishSiteService implements UnpublishSiteUseCase {

    private final SiteOwnershipGuard siteOwnershipGuard;
    private final PublishedSiteRepository publishedSiteRepository;

    @Transactional
    @Override
    public void unpublishSite(UnpublishSiteCommand command) {
        log.debug("Unpublishing site with id {}", command.siteId());
        siteOwnershipGuard.requireOwnershipSite(command.siteId(), command.userId());
        publishedSiteRepository.deleteBySiteId(command.siteId());
        log.debug("Site with id {} unpublished successfully", command.siteId());
    }
}
