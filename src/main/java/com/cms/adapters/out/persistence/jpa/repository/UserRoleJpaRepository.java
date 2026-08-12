package com.cms.adapters.out.persistence.jpa.repository;

import com.cms.adapters.out.persistence.jpa.entity.UserRoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleJpaRepository extends JpaRepository<UserRoleJpaEntity, UserRoleJpaEntity.UserRoleId> {
    List<UserRoleJpaEntity> findByUserId(Long userId);
}
