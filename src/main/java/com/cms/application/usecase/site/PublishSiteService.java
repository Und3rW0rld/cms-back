package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.model.site.PublishedSite;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.PublishSiteCommand;
import com.cms.domain.port.in.site.PublishSiteUseCase;
import com.cms.domain.port.out.DraftSiteRepository;
import com.cms.domain.port.out.PublishedSiteRepository;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublishSiteService implements PublishSiteUseCase {

    private final SiteOwnershipGuard siteOwnershipGuard;
    private final PublishedSiteRepository publishedSiteRepository;
    private final DraftSiteRepository draftSiteRepository;
    private final SiteRepository siteRepository;

    @Transactional
    @Override
    public SiteWithPublicationState publishSite(PublishSiteCommand command) {
        log.debug("Publishing site with id {} for user {}", command.siteId(), command.userId());

        siteOwnershipGuard.requireOwnershipSite(command.siteId(), command.userId());

        DraftSite draft = draftSiteRepository.getDraftSiteById(command.siteId()).orElseThrow(
                () -> new NotFoundException("Draft site with id " + command.siteId() + " not found")
        );

        publishedSiteRepository.upsert(
                new PublishedSite(command.siteId(), draft.content(), Instant.now())
        );

        return siteRepository.findByIdWithPublicationState(command.siteId()).orElseThrow(
                () -> new NotFoundException("Site with id " + command.siteId() + " not found"));

    }

}
