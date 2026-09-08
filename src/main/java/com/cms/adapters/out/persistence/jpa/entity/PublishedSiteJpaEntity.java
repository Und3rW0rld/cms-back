package com.cms.adapters.out.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "site_published")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedSiteJpaEntity {

    @Id
    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    private String content;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;
}
