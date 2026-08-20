package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.SiteJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.SiteJpaRepository;
import com.cms.domain.model.site.Site;
import com.cms.domain.model.site.SiteWithPublicationState;
import com.cms.domain.port.out.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.CONTENT_SCHEMA;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.CREATED_AT;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.ID;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.IS_PUBLISHED;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.OWNER_USER_ID;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.SITE_ID;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.SUMMARY;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.TITLE;
import static com.cms.adapters.out.persistence.jpa.adapter.SiteSqlColumns.UPDATED_AT;

@Component
@RequiredArgsConstructor
public class SitePersistenceAdapter implements SiteRepository {

    private static final String SITE_COLUMNS =
            String.join(", s.", List.of(ID, OWNER_USER_ID, TITLE, SUMMARY, CONTENT_SCHEMA, CREATED_AT, UPDATED_AT));

    private static final String SELECT_WITH_PUBLICATION_STATE =
            "SELECT s." + SITE_COLUMNS + ", (sp." + SITE_ID + " IS NOT NULL) AS " + IS_PUBLISHED
                    + " FROM sites s LEFT JOIN site_published sp ON sp." + SITE_ID + " = s." + ID + " ";

    private static final String FIND_WITH_PUBLICATION_STATE_SQL =
            SELECT_WITH_PUBLICATION_STATE + "WHERE s." + ID + " = ?";

    private static final String FIND_BY_OWNER_WITH_PUBLICATION_STATE_SQL =
            SELECT_WITH_PUBLICATION_STATE + "WHERE s." + OWNER_USER_ID + " = ? ORDER BY s." + CREATED_AT + " DESC";

    private final SiteJpaRepository siteJpaRepository;
    private final JdbcTemplate jdbcTemplate;

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

    @Override
    public Optional<SiteWithPublicationState> findByIdWithPublicationState(UUID id) {
        List<SiteWithPublicationState> results = jdbcTemplate.query(
                FIND_WITH_PUBLICATION_STATE_SQL, this::mapRow, id);
        return results.stream().findFirst();
    }

    @Override
    public List<SiteWithPublicationState> findByOwnerWithPublicationState(Long ownerUserId) {
        return jdbcTemplate.query(FIND_BY_OWNER_WITH_PUBLICATION_STATE_SQL, this::mapRow, ownerUserId);
    }

    SiteWithPublicationState mapRow(ResultSet rs, int rowNum) throws SQLException {
        Site site = new Site(
                UUID.fromString(rs.getString(ID)),
                rs.getLong(OWNER_USER_ID),
                rs.getString(TITLE),
                rs.getString(SUMMARY),
                rs.getString(CONTENT_SCHEMA),
                rs.getTimestamp(CREATED_AT).toInstant(),
                rs.getTimestamp(UPDATED_AT).toInstant()
        );
        return new SiteWithPublicationState(site, rs.getBoolean(IS_PUBLISHED));
    }

    private Site toDomain(SiteJpaEntity entity) {
        return new Site(entity.getId(), entity.getOwnerUserId(), entity.getTitle(), entity.getSummary(),
                entity.getContentSchema(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
