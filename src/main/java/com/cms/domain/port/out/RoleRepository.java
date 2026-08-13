package com.cms.domain.port.out;

import com.cms.domain.model.user.Role;

import java.util.Set;

public interface RoleRepository {

    void assignRole(Long userId, Role role);

    Set<Role> findRolesByUserId(Long userId);
}
