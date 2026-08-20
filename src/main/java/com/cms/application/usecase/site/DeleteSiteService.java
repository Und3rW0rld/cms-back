package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.DeleteSiteCommand;
import com.cms.domain.port.in.site.DeleteSiteUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteSiteService implements DeleteSiteUseCase {

    private final SiteRepository siteRepository;

    @Override
    @Transactional
    public void delete(DeleteSiteCommand command) {
        Site existing = siteRepository.findById(command.siteId())
                .orElseThrow(() -> new NotFoundException("Site not found: " + command.siteId()));

        if (!existing.ownerUserId().equals(command.requesterUserId())) {
            throw new AccessDeniedException("You do not have access to this site");
        }

        siteRepository.deleteById(command.siteId());
    }
}
