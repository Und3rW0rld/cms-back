package com.cms.domain.model.user;

import java.time.Instant;
import java.util.Set;

public record User(
        Long id,
        String email,
        String name,
        boolean enabled,
        Set<Role> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
