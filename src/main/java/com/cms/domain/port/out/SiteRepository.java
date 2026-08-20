package com.cms.domain.port.out;

import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRepository {

    Site save(Site site);

    Optional<Site> findById(UUID id);

    List<Site> findByOwner(Long ownerUserId);

    void deleteById(UUID id);

    Optional<SiteWithPublicationState> findByIdWithPublicationState(UUID id);

    List<SiteWithPublicationState> findByOwnerWithPublicationState(Long ownerUserId);
}
