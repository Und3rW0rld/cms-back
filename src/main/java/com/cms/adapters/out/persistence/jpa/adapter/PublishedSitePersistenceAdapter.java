package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.repository.PublishedSiteJpaRepository;
import com.cms.domain.model.site.PublishedSite;
import com.cms.domain.port.out.PublishedSiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

import static com.cms.adapters.out.persistence.jpa.adapter.PublishedSiteColumns.*;

@Component
@RequiredArgsConstructor
public class PublishedSitePersistenceAdapter implements PublishedSiteRepository {

    private static final String INSERT_PUBLISHED_SITE_SQL = """
        INSERT INTO site_published (%s, %s, %s)
        VALUES (?, ?::jsonb, ?)
        ON CONFLICT (%s) DO UPDATE
        SET %s = EXCLUDED.%s,
            %s = EXCLUDED.%s
        """.formatted(SITE_ID,
            CONTENT,
            PUBLISHED_AT,
            SITE_ID,
            CONTENT,
            CONTENT,
            PUBLISHED_AT,
            PUBLISHED_AT
    );

    private static final String DELETE_PUBLISHED_SITE_SQL = """
        DELETE FROM site_published WHERE %s = ?
        """.formatted(SITE_ID);

    private final PublishedSiteJpaRepository publishedSiteJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void upsert(PublishedSite publishedSite) {
        jdbcTemplate.update(
                INSERT_PUBLISHED_SITE_SQL,
                publishedSite.id(),
                publishedSite.content().rawJson(),
                Timestamp.from(publishedSite.publishedAt())
        );
    }

    @Override
    public void deleteBySiteId(UUID siteId) {
        jdbcTemplate.update(DELETE_PUBLISHED_SITE_SQL, siteId);
    }
}
