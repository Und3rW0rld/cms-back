package com.cms.adapters.out.persistence.jpa.repository;

import com.cms.adapters.out.persistence.jpa.entity.UserCredentialJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCredentialJpaRepository extends JpaRepository<UserCredentialJpaEntity, Long> {
    Optional<UserCredentialJpaEntity> findByUserId(Long userId);
}
