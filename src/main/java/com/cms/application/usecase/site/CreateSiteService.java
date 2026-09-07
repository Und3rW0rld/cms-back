package com.cms.application.usecase.site;

import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.CreateSiteCommand;
import com.cms.domain.port.in.site.CreateSiteUseCase;
import com.cms.domain.port.out.DraftSiteRepository;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateSiteService implements CreateSiteUseCase {

    private final SiteRepository siteRepository;
    private final DraftSiteRepository draftSiteRepository;

    @Override
    @Transactional
    public Site create(CreateSiteCommand command) {
        Instant now = Instant.now();
        Site toSave = new Site(null, command.ownerUserId(), command.title(), command.summary(),
                command.contentSchema(), now, now);

        log.debug("Creating site for user {} with title {} and content schema {}", command.ownerUserId(), command.title(), command.contentSchema());
        Site savedSite = siteRepository.save(toSave);

        log.debug("Creating default draft site for site {}", savedSite.id());
        draftSiteRepository.save(new DraftSite(savedSite.id(), 1L, Content.empty(), now));

        return savedSite;
    }
}
