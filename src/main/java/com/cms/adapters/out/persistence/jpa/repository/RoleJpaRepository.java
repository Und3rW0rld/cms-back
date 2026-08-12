package com.cms.adapters.out.persistence.jpa.repository;

import com.cms.adapters.out.persistence.jpa.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, Integer> {
    List<RoleJpaEntity> findByIdIn(List<Integer> ids);
}
