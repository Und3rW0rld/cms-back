package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.SiteJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.SiteJpaRepository;
import com.cms.domain.model.site.Site;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SitePersistenceAdapter implements SiteRepository {

    private final SiteJpaRepository siteJpaRepository;

    @Override
    public Site save(Site site) {
        SiteJpaEntity entity = SiteJpaEntity.builder()
                .id(site.id())
                .ownerUserId(site.ownerUserId())
                .title(site.title())
                .summary(site.summary())
                .contentSchema(site.contentSchema())
                .build();

        SiteJpaEntity saved = siteJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Site> findById(UUID id) {
        return siteJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Site> findByOwner(Long ownerUserId) {
        return siteJpaRepository.findByOwnerUserId(ownerUserId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        siteJpaRepository.deleteById(id);
    }

    private Site toDomain(SiteJpaEntity entity) {
        return new Site(entity.getId(), entity.getOwnerUserId(), entity.getTitle(), entity.getSummary(),
                entity.getContentSchema(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
