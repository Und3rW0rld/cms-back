package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.repository.PublishedSiteJpaRepository;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.PublishedSite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublishedSitePersistenceAdapterTest {

    @Mock
    private PublishedSiteJpaRepository publishedSiteJpaRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PublishedSitePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PublishedSitePersistenceAdapter(publishedSiteJpaRepository, jdbcTemplate);
    }

    @Test
    void shouldUpsertPublishedSiteWithOnConflictSql() {
        UUID siteId = UUID.randomUUID();
        Instant publishedAt = Instant.parse("2026-08-15T14:00:00Z");
        String rawJson = "{\"title\":\"Portfolio\"}";
        PublishedSite publishedSite = new PublishedSite(siteId, new Content(rawJson), publishedAt);

        adapter.upsert(publishedSite);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(siteId),
                eq(rawJson),
                eq(Timestamp.from(publishedAt))
        );

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("INSERT INTO site_published");
        assertThat(sql).contains("ON CONFLICT");
        assertThat(sql).contains("DO UPDATE");
        assertThat(sql).contains("?::jsonb");
    }

    @Test
    void shouldPassAssignedSiteIdNotAGeneratedOne() {
        UUID siteId = UUID.randomUUID();
        Instant publishedAt = Instant.now();
        PublishedSite publishedSite = new PublishedSite(siteId, Content.empty(), publishedAt);

        adapter.upsert(publishedSite);

        verify(jdbcTemplate).update(anyString(), eq(siteId), any(), any());
    }

    @Test
    void shouldDeletePublishedSiteBySiteId() {
        UUID siteId = UUID.randomUUID();

        adapter.deleteBySiteId(siteId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), eq(siteId));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("DELETE FROM site_published");
        assertThat(sql).contains("site_id");
    }
}
