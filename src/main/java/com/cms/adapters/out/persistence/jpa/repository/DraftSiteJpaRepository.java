package com.cms.adapters.out.persistence.jpa.repository;

import com.cms.adapters.out.persistence.jpa.entity.DraftSiteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DraftSiteJpaRepository extends JpaRepository<DraftSiteJpaEntity, UUID> {
}
