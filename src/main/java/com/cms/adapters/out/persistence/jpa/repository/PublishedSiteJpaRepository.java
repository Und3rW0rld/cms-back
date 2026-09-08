package com.cms.adapters.out.persistence.jpa.repository;

import com.cms.adapters.out.persistence.jpa.entity.PublishedSiteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PublishedSiteJpaRepository extends JpaRepository<PublishedSiteJpaEntity, UUID> {
}
