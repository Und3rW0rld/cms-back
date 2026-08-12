package com.cms.adapters.in.security;

import com.cms.adapters.config.SecurityConstants;
import com.cms.adapters.out.persistence.jpa.entity.RoleJpaEntity;
import com.cms.adapters.out.persistence.jpa.entity.UserJpaEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CmsUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Collection<GrantedAuthority> authorities;

    public CmsUserDetails(Long userId, String email, String passwordHash, boolean enabled,
                           Collection<GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities;
    }


    public static CmsUserDetails from(UserJpaEntity user, String passwordHash, Set<RoleJpaEntity> roles) {
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + role.getName()))
                .collect(Collectors.toList());

        return new CmsUserDetails(user.getId(), user.getEmail(), passwordHash, user.isEnabled(), authorities);
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
