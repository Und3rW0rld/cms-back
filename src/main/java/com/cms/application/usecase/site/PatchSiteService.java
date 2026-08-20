package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.PatchSiteCommand;
import com.cms.domain.port.in.site.PatchSiteUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PatchSiteService implements PatchSiteUseCase {

    private final SiteRepository siteRepository;

    @Override
    @Transactional
    public SiteWithPublicationState patch(PatchSiteCommand command) {

        SiteWithPublicationState current = siteRepository.findByIdWithPublicationState(command.siteId())
                .orElseThrow(() -> new NotFoundException("Site not found: " + command.siteId()));

        Site existing = current.site();
        if (!existing.ownerUserId().equals(command.requesterUserId())) {
            throw new AccessDeniedException("You do not have access to this site");
        }

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
        return new SiteWithPublicationState(saved, current.published());
    }
}
