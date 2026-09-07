package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.DraftSiteJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.DraftSiteJpaRepository;
import com.cms.domain.exception.VersionMismatchException;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DraftSitePersistenceAdapterTest {

    @Mock
    DraftSiteJpaRepository draftSiteJpaRepository;

    @Mock
    JdbcTemplate jdbcTemplate;

    private DraftSitePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {adapter = new DraftSitePersistenceAdapter(draftSiteJpaRepository, jdbcTemplate);}

    @Test
    void shouldGetDraftSiteById() {
        UUID draftSiteId = UUID.randomUUID();
        Instant now = Instant.now();
        DraftSite draftSite = new DraftSite(draftSiteId, 1L, new Content("{}"), now);
        DraftSiteJpaEntity entity = getDefaultDraftSiteJpaEntity();
        entity.setId(draftSiteId);
        entity.setUpdatedAt(now);

        when(draftSiteJpaRepository.findById(draftSiteId)).thenReturn(Optional.of(entity));

        Optional<DraftSite> found = adapter.getDraftSiteById(draftSiteId);

        assertThat(found).isPresent();
        assertThat(found).isPresent().contains(draftSite);
    }

    @Test
    void shouldReturnEmptyWhenDraftSiteNotFound() {
        UUID draftSiteId = UUID.randomUUID();

        when(draftSiteJpaRepository.findById(draftSiteId)).thenReturn(Optional.empty());

        Optional<DraftSite> found = adapter.getDraftSiteById(draftSiteId);

        assertThat(found).isEmpty();
    }

    @Test
    void shouldSaveDraftSite() {
        UUID draftSiteId = UUID.randomUUID();
        Instant now = Instant.now();
        DraftSite toSave = new DraftSite(draftSiteId, 1L, new Content("{}"), now);

        DraftSiteJpaEntity entity = DraftSiteJpaEntity.builder()
                .id(draftSiteId)
                .version(1L)
                .content("{}")
                .updatedAt(now)
                .build();

        when(draftSiteJpaRepository.save(any(DraftSiteJpaEntity.class))).thenReturn(entity);

        DraftSite saved = adapter.save(toSave);

        assertThat(saved).isEqualTo(toSave);
    }

    @Test
    void shouldUpdateDraftWhenExpectedVersionMatches() {
        UUID draftSiteId = UUID.randomUUID();
        long expectedVersion = 1L;
        Content newContent = new Content("{\"title\":\"Updated\"}");
        Instant now = Instant.now();

        DraftSite updatedDraft = new DraftSite(draftSiteId, expectedVersion + 1, newContent, now);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of(updatedDraft));

        DraftSite result = adapter.update(draftSiteId, newContent, expectedVersion);

        assertThat(result).isEqualTo(updatedDraft);
    }

    @Test
    void shouldThrowWhenUpdateAffectsNoRows() {
        UUID draftSiteId = UUID.randomUUID();
        Content newContent = new Content("{\"title\":\"Updated\"}");
        long expectedVersion = 1L;

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> adapter.update(draftSiteId, newContent, expectedVersion))
                .isInstanceOf(VersionMismatchException.class);
    }

    DraftSiteJpaEntity getDefaultDraftSiteJpaEntity() {
        return DraftSiteJpaEntity.builder().id(UUID.randomUUID()).updatedAt(Instant.now()).content("{}").version(1L).build();
    }

}
