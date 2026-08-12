package com.cms.adapters.config;

import com.cms.adapters.in.security.CmsUserDetails;
import com.cms.adapters.out.persistence.jpa.entity.RoleJpaEntity;
import com.cms.adapters.out.persistence.jpa.entity.UserCredentialJpaEntity;
import com.cms.adapters.out.persistence.jpa.entity.UserJpaEntity;
import com.cms.adapters.out.persistence.jpa.entity.UserRoleJpaEntity;
import com.cms.adapters.out.persistence.jpa.repository.RoleJpaRepository;
import com.cms.adapters.out.persistence.jpa.repository.UserCredentialJpaRepository;
import com.cms.adapters.out.persistence.jpa.repository.UserJpaRepository;
import com.cms.adapters.out.persistence.jpa.repository.UserRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserJpaRepository userJpaRepository;
    private final UserCredentialJpaRepository userCredentialJpaRepository;
    private final UserRoleJpaRepository userRoleJpaRepository;
    private final RoleJpaRepository roleJpaRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> {
            UserJpaEntity user = userJpaRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            String passwordHash = userCredentialJpaRepository.findByUserId(user.getId())
                    .map(UserCredentialJpaEntity::getPasswordHash)
                    .orElseThrow(() -> new UsernameNotFoundException("Credentials not found for: " + email));

            List<Integer> roleIds = userRoleJpaRepository.findByUserId(user.getId()).stream()
                    .map(UserRoleJpaEntity::getRoleId)
                    .toList();

            Set<RoleJpaEntity> roles = new HashSet<>(roleJpaRepository.findByIdIn(roleIds));

            return CmsUserDetails.from(user, passwordHash, roles);
        };
    }
}
