package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.UserCredentialJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.UserCredentialJpaRepository;
import com.cms.domain.port.out.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CredentialPersistenceAdapter implements CredentialRepository {

    private final UserCredentialJpaRepository userCredentialJpaRepository;

    @Override
    public void save(Long userId, String passwordHash) {
        UserCredentialJpaEntity entity = UserCredentialJpaEntity.builder()
                .userId(userId)
                .passwordHash(passwordHash)
                .build();
        userCredentialJpaRepository.save(entity);
    }

    @Override
    public Optional<String> findPasswordHashByUserId(Long userId) {
        return userCredentialJpaRepository.findByUserId(userId).map(UserCredentialJpaEntity::getPasswordHash);
    }
}
