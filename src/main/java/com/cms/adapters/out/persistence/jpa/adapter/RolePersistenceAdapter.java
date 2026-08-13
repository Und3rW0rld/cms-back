package com.cms.adapters.out.persistence.jpa.adapter;

import com.cms.adapters.out.persistence.jpa.entity.RoleJpaEntity;
import com.cms.adapters.out.persistence.jpa.entity.UserRoleJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.RoleJpaRepository;
import com.cms.adapters.out.persistence.jpa.repository.UserRoleJpaRepository;
import com.cms.domain.model.user.Role;
import com.cms.domain.port.out.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RolePersistenceAdapter implements RoleRepository {

    private final RoleJpaRepository roleJpaRepository;
    private final UserRoleJpaRepository userRoleJpaRepository;

    @Override
    public void assignRole(Long userId, Role role) {
        RoleJpaEntity roleEntity = roleJpaRepository.findByName(role.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Role '" + role.name() + "' has no seed row in DB"));

        UserRoleJpaEntity.UserRoleId id = new UserRoleJpaEntity.UserRoleId(userId, roleEntity.getId());
        if (userRoleJpaRepository.existsById(id)) {
            return;
        }

        userRoleJpaRepository.save(UserRoleJpaEntity.builder()
                .userId(userId)
                .roleId(roleEntity.getId())
                .build());
    }

    @Override
    public Set<Role> findRolesByUserId(Long userId) {
        List<Integer> roleIds = userRoleJpaRepository.findByUserId(userId).stream()
                .map(UserRoleJpaEntity::getRoleId)
                .toList();
        return roleJpaRepository.findByIdIn(roleIds).stream()
                .map(entity -> Role.valueOf(entity.getName()))
                .collect(Collectors.toSet());
    }
}
