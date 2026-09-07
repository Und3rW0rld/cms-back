package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SiteOwnershipGuard {

    private final SiteRepository siteRepository;

    public Site requireOwnershipSite(UUID siteId, long requesterUserId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new NotFoundException("Site not found: " + siteId));
        assertOwnership(site.ownerUserId(), requesterUserId);

        return site;
    }

    public SiteWithPublicationState requireOwnershipSiteWithPublicationState(UUID siteId, long requesterUserId) {
        SiteWithPublicationState site = siteRepository.findByIdWithPublicationState(siteId)
                .orElseThrow(() -> new NotFoundException("Site not found: " + siteId));
        assertOwnership(site.site().ownerUserId(), requesterUserId);

        return site;
    }

    private void assertOwnership(long userId, long requesterUserId) {
        if (userId != requesterUserId) {
            throw new AccessDeniedException("You do not have access to this site");
        }
    }


}
