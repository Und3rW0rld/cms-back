package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.SiteJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.SiteJpaRepository;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SitePersistenceAdapterTest {

    @Mock
    private SiteJpaRepository siteJpaRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SitePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SitePersistenceAdapter(siteJpaRepository, jdbcTemplate);
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

    @Test
    void shouldMapRowToSiteWithPublicationStateTrue() throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn(id.toString());
        when(rs.getLong("owner_user_id")).thenReturn(1L);
        when(rs.getString("title")).thenReturn("My Portfolio");
        when(rs.getString("summary")).thenReturn("A short summary");
        when(rs.getString("content_schema")).thenReturn("portfolio-v1");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
        when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));
        when(rs.getBoolean("is_published")).thenReturn(true);

        SiteWithPublicationState result = adapter.mapRow(rs, 0);

        assertThat(result.site().id()).isEqualTo(id);
        assertThat(result.site().ownerUserId()).isEqualTo(1L);
        assertThat(result.site().title()).isEqualTo("My Portfolio");
        assertThat(result.published()).isTrue();
    }

    @Test
    void shouldMapRowToSiteWithPublicationStateFalseWhenNoPublishedRow() throws SQLException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn(id.toString());
        when(rs.getLong("owner_user_id")).thenReturn(1L);
        when(rs.getString("title")).thenReturn("Draft Only Site");
        when(rs.getString("summary")).thenReturn(null);
        when(rs.getString("content_schema")).thenReturn(null);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
        when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));
        when(rs.getBoolean("is_published")).thenReturn(false);

        SiteWithPublicationState result = adapter.mapRow(rs, 0);

        assertThat(result.published()).isFalse();
        assertThat(result.site().summary()).isNull();
    }

    @Test
    void shouldReturnPresentOptionalWhenFindByIdWithPublicationStateHasResult() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Site site = new Site(id, 1L, "My Portfolio", null, null, now, now);
        SiteWithPublicationState expected = new SiteWithPublicationState(site, true);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(id)))
                .thenReturn(List.of(expected));

        Optional<SiteWithPublicationState> result = adapter.findByIdWithPublicationState(id);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyOptionalWhenFindByIdWithPublicationStateHasNoResult() {
        UUID id = UUID.randomUUID();

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(id)))
                .thenReturn(List.of());

        Optional<SiteWithPublicationState> result = adapter.findByIdWithPublicationState(id);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllSitesWithPublicationStateForOwner() {
        Long ownerUserId = 42L;
        Instant now = Instant.now();
        Site published = new Site(UUID.randomUUID(), ownerUserId, "Published Site", null, null, now, now);
        Site draft = new Site(UUID.randomUUID(), ownerUserId, "Draft Site", null, null, now, now);
        List<SiteWithPublicationState> expected = List.of(
                new SiteWithPublicationState(published, true),
                new SiteWithPublicationState(draft, false)
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(ownerUserId)))
                .thenReturn(expected);

        List<SiteWithPublicationState> result = adapter.findByOwnerWithPublicationState(ownerUserId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SiteWithPublicationState::published).containsExactly(true, false);
    }
}
