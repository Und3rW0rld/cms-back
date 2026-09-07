package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.DraftSiteJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.DraftSiteJpaRepository;
import com.cms.domain.exception.VersionMismatchException;
import com.cms.domain.model.site.Content;
import com.cms.domain.model.site.DraftSite;
import com.cms.domain.port.out.DraftSiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cms.adapters.out.persistence.jpa.adapter.DraftSiteColumns.*;

@Component
@RequiredArgsConstructor
public class DraftSitePersistenceAdapter implements DraftSiteRepository {

    private static final String UPDATE_CONTENT_SQL = """
        UPDATE site_drafts
        SET %s = ?::jsonb,
            %s = %s + 1,
            %s = NOW()
        WHERE %s = ?
          AND %s = ?
          RETURNING %s, %s, %s, %s
        """.formatted(
            CONTENT,
            VERSION,
            VERSION,
            UPDATED_AT,
            SITE_ID,
            VERSION,
            SITE_ID,
            VERSION,
            CONTENT,
            UPDATED_AT
    );

    private final DraftSiteJpaRepository draftSiteJpaRepository;

    private final JdbcTemplate jdbcTemplate;


    @Override
    public Optional<DraftSite> getDraftSiteById(UUID siteId) {
        return draftSiteJpaRepository.findById(siteId).map(this::toDomain);
    }

    @Override
    public DraftSite save(DraftSite draftSite) {
        DraftSiteJpaEntity entity = toEntity(draftSite);
        DraftSiteJpaEntity savedEntity = draftSiteJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public DraftSite update(UUID siteId, Content content, long expectedVersion) {

        List<DraftSite> results = jdbcTemplate.query(
                UPDATE_CONTENT_SQL,
                this::mapRow,
                content.rawJson(),
                siteId,
                expectedVersion
        );

        if (results.isEmpty()) {
            throw new VersionMismatchException(
                    "The resource was modified since it was last retrieved"
            );
        }

        return results.getFirst();
    }

    private DraftSite mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DraftSite(
                UUID.fromString(rs.getString(SITE_ID)),
                rs.getLong(VERSION),
                new Content(rs.getString(CONTENT)),
                rs.getTimestamp(UPDATED_AT).toInstant()
        );
    }

    private DraftSite toDomain(DraftSiteJpaEntity entity) {
        Content content = new Content(entity.getContent());
        return new DraftSite(entity.getId(), entity.getVersion(), content , entity.getUpdatedAt());
    }

    private DraftSiteJpaEntity toEntity(DraftSite draftSite) {
        return DraftSiteJpaEntity.builder().id(draftSite.id())
                .version(draftSite.version())
                .content(draftSite.content().rawJson())
                .updatedAt(draftSite.updatedAt())
                .build();
    }
}
