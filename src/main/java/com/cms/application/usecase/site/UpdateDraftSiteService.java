package com.cms.application.usecase.site;

import com.cms.domain.exception.NotFoundException;
import com.cms.domain.exception.VersionMismatchException;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.port.in.site.UpdateDraftSiteCommand;
import com.cms.domain.port.in.site.UpdateDraftSiteUseCase;
import com.cms.domain.port.out.DraftSiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateDraftSiteService implements UpdateDraftSiteUseCase {

    private final SiteOwnershipGuard guard;
    private final DraftSiteRepository repository;

    @Override
    @Transactional
    public DraftSite updateDraftSite(UpdateDraftSiteCommand command) {
        log.debug("Updating draft site with id {} for user {}", command.id(), command.userId());
        guard.requireOwnershipSite(command.id(), command.userId());

        DraftSite currentSite = repository.getDraftSiteById(command.id()).orElseThrow(
                () -> new NotFoundException(
                        "Draft site with id " + command.id() + " not found"
                )
        );
        validateVersion(currentSite, command.expectedVersion());

        return repository.update(command.id(), new Content(command.content()), command.expectedVersion());
    }


    private void validateVersion(DraftSite currentSite, long version) {
        log.debug("Validating expectedVersion {} with current expectedVersion {}", version, currentSite.version());
        if (version != currentSite.version()) {throw new VersionMismatchException("Version mismatch");}
    }
}
