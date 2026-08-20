package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.GetSiteCommand;
import com.cms.domain.port.in.site.GetSiteUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetSiteService implements GetSiteUseCase {

    private final SiteRepository siteRepository;

    @Override
    public SiteWithPublicationState getById(GetSiteCommand command) {
        SiteWithPublicationState result = siteRepository.findByIdWithPublicationState(command.siteId())
                .orElseThrow(() -> new NotFoundException("Site not found: " + command.siteId()));

        if (!result.site().ownerUserId().equals(command.requesterUserId())) {
            throw new AccessDeniedException("You do not have access to this site");
        }

        return result;
    }
}
