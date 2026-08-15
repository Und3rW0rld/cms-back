package com.cms.adapters.out.persistence.jpa.repository;

import com.cms.adapters.out.persistence.jpa.entity.SiteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SiteJpaRepository extends JpaRepository<SiteJpaEntity, UUID> {
    List<SiteJpaEntity> findByOwnerUserId(Long ownerUserId);
}
