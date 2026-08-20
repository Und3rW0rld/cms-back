package com.cms.application.usecase.site;

import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.in.site.ListSitesUseCase;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListSitesService implements ListSitesUseCase {

    private final SiteRepository siteRepository;

    @Override
    public List<SiteWithPublicationState> listByOwner(Long ownerUserId) {
        return siteRepository.findByOwnerWithPublicationState(ownerUserId);
    }
}
