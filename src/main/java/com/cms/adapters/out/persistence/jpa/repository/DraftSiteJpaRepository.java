package com.cms.adapters.out.persistence.jpa.repository;

import com.cms.adapters.out.persistence.jpa.entity.DraftSiteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface DraftSiteJpaRepository extends JpaRepository<DraftSiteJpaEntity, UUID> {
    @Modifying
    @Query("""
        UPDATE DraftSiteJpaEntity d
        SET d.content = :content,
            d.version = d.version + 1,
            d.updatedAt = :updatedAt
        WHERE d.id = :id
          AND d.version = :expectedVersion
        """)
    int updateContent(
            @Param("id") UUID id,
            @Param("content") String content,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion
    );
}
