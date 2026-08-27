package com.cms.application.usecase.site;

import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.PatchSiteCommand;
import com.cms.domain.port.in.site.PatchSiteUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatchSiteService implements PatchSiteUseCase {

    private final SiteRepository siteRepository;
    private final SiteOwnershipGuard siteOwnershipGuard;

    @Override
    @Transactional
    public SiteWithPublicationState patch(PatchSiteCommand command) {
        log.debug("Patching site with id {} for user {}", command.siteId(), command.requesterUserId());
        SiteWithPublicationState current = siteOwnershipGuard.requireOwnershipSiteWithPublicationState(command.siteId(), command.requesterUserId());

        Site existing = current.site();

        Site patched = new Site(
                existing.id(),
                existing.ownerUserId(),
                command.title() != null ? command.title() : existing.title(),
                command.summary() != null ? command.summary() : existing.summary(),
                command.contentSchema() != null ? command.contentSchema() : existing.contentSchema(),
                existing.createdAt(),
                Instant.now()
        );

        Site saved = siteRepository.save(patched);
        log.debug("Patched site with id {} for user {}", saved.id(), command.requesterUserId());
        return new SiteWithPublicationState(saved, current.published());
    }
}
