package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.UserJpaEntity;
import com.cms.adapters.out.persistence.jpa.entity.UserRoleJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.RoleJpaRepository;
import com.cms.adapters.out.persistence.jpa.repository.UserJpaRepository;
import com.cms.adapters.out.persistence.jpa.repository.UserRoleJpaRepository;
import com.cms.domain.model.user.Role;
import com.cms.domain.model.user.User;
import com.cms.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserRoleJpaRepository userRoleJpaRepository;
    private final RoleJpaRepository roleJpaRepository;

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.builder()
                .id(user.id())
                .email(user.email())
                .name(user.name())
                .enabled(user.enabled())
                .build();

        UserJpaEntity saved = userJpaRepository.save(entity);
        return toDomain(saved, user.roles());
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id).map(entity -> toDomain(entity, loadRoles(entity.getId())));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(entity -> toDomain(entity, loadRoles(entity.getId())));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    private Set<Role> loadRoles(Long userId) {
        List<Integer> roleIds = userRoleJpaRepository.findByUserId(userId).stream()
                .map(UserRoleJpaEntity::getRoleId)
                .toList();
        return roleJpaRepository.findByIdIn(roleIds).stream()
                .map(role -> Role.valueOf(role.getName()))
                .collect(Collectors.toSet());
    }

    private User toDomain(UserJpaEntity entity, Set<Role> roles) {
        return new User(entity.getId(), entity.getEmail(), entity.getName(), entity.isEnabled(),
                roles, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
