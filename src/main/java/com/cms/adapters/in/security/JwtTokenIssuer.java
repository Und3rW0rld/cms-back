package com.cms.adapters.in.security;

import com.cms.adapters.in.security.jwt.JwtProvider;
import com.cms.domain.model.user.AuthToken;
import com.cms.domain.port.out.TokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Loads the full CmsUserDetails to embed userId + roles as JWT claims —
 * lets JwtAuthenticationFilter skip the DB round-trip on every request.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Override
    public AuthToken issueToken(String subject) {
        CmsUserDetails userDetails = (CmsUserDetails) userDetailsService.loadUserByUsername(subject);

        List<String> roleNames = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> claims = Map.of(
                JwtProvider.CLAIM_USER_ID, userDetails.getUserId(),
                JwtProvider.CLAIM_ROLES, roleNames
        );

        String token = jwtProvider.generateToken(userDetails, claims);
        return new AuthToken(token, jwtExpiration);
    }
}
