package com.cms.domain.port.out;

import java.util.Optional;

public interface CredentialRepository {

    void save(Long userId, String passwordHash);

    Optional<String> findPasswordHashByUserId(Long userId);
}
