package com.cms.application.usecase.site;

import com.cms.domain.model.site.Site;
import com.cms.domain.port.in.site.CreateSiteCommand;
import com.cms.domain.port.in.site.CreateSiteUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CreateSiteService implements CreateSiteUseCase {

    private final SiteRepository siteRepository;

    @Override
    @Transactional
    public Site create(CreateSiteCommand command) {
        Instant now = Instant.now();
        Site toSave = new Site(null, command.ownerUserId(), command.title(), command.summary(),
                command.contentSchema(), now, now);
        return siteRepository.save(toSave);
    }
}
