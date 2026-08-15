package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.SiteJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.SiteJpaRepository;
import com.cms.domain.model.site.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SitePersistenceAdapterTest {

    @Mock
    private SiteJpaRepository siteJpaRepository;

    private SitePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SitePersistenceAdapter(siteJpaRepository);
    }

    @Test
    void shouldRoundTripSiteThroughSaveAndFindById() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Site toSave = new Site(id, 1L, "My Portfolio", "A short summary", "portfolio-v1", now, now);

        SiteJpaEntity savedEntity = SiteJpaEntity.builder()
                .id(id)
                .ownerUserId(1L)
                .title("My Portfolio")
                .summary("A short summary")
                .contentSchema("portfolio-v1")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(siteJpaRepository.save(any(SiteJpaEntity.class))).thenReturn(savedEntity);
        when(siteJpaRepository.findById(id)).thenReturn(Optional.of(savedEntity));

        Site saved = adapter.save(toSave);

        ArgumentCaptor<SiteJpaEntity> captor = ArgumentCaptor.forClass(SiteJpaEntity.class);
        verify(siteJpaRepository).save(captor.capture());
        SiteJpaEntity persistedArg = captor.getValue();
        assertThat(persistedArg.getId()).isEqualTo(id);
        assertThat(persistedArg.getOwnerUserId()).isEqualTo(1L);
        assertThat(persistedArg.getTitle()).isEqualTo("My Portfolio");
        assertThat(persistedArg.getSummary()).isEqualTo("A short summary");
        assertThat(persistedArg.getContentSchema()).isEqualTo("portfolio-v1");

        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.ownerUserId()).isEqualTo(1L);
        assertThat(saved.title()).isEqualTo("My Portfolio");
        assertThat(saved.summary()).isEqualTo("A short summary");
        assertThat(saved.contentSchema()).isEqualTo("portfolio-v1");
        assertThat(saved.createdAt()).isEqualTo(now);
        assertThat(saved.updatedAt()).isEqualTo(now);

        Optional<Site> found = adapter.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    @Test
    void shouldReturnEmptyWhenSiteNotFound() {
        UUID id = UUID.randomUUID();
        when(siteJpaRepository.findById(id)).thenReturn(Optional.empty());

        Optional<Site> found = adapter.findById(id);

        assertThat(found).isEmpty();
    }

    @Test
    void shouldMapAllSitesOwnedByUser() {
        Long ownerUserId = 42L;
        Instant now = Instant.now();
        SiteJpaEntity entity1 = SiteJpaEntity.builder()
                .id(UUID.randomUUID()).ownerUserId(ownerUserId).title("Site A")
                .createdAt(now).updatedAt(now).build();
        SiteJpaEntity entity2 = SiteJpaEntity.builder()
                .id(UUID.randomUUID()).ownerUserId(ownerUserId).title("Site B")
                .createdAt(now).updatedAt(now).build();

        when(siteJpaRepository.findByOwnerUserId(ownerUserId)).thenReturn(List.of(entity1, entity2));

        List<Site> sites = adapter.findByOwner(ownerUserId);

        assertThat(sites).hasSize(2);
        assertThat(sites).extracting(Site::title).containsExactly("Site A", "Site B");
        assertThat(sites).allMatch(site -> site.ownerUserId().equals(ownerUserId));
    }

    @Test
    void shouldDelegateDeleteToRepository() {
        UUID id = UUID.randomUUID();

        adapter.deleteById(id);

        verify(siteJpaRepository).deleteById(id);
    }
}
