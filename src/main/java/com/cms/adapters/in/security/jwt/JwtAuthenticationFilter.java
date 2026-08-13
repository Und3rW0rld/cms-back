package com.cms.adapters.in.security.jwt;

import com.cms.adapters.config.SecurityConstants;
import com.cms.adapters.in.security.CmsUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Builds CmsUserDetails directly from the JWT's userId + roles claims —
 * no DB round-trip per request. Safe because the token is signed (can't be
 * forged) and roles never change after registration today (no role
 * management use case exists — see docs §14). isEnabled() is not re-checked
 * here; a disabled account keeps working until its token expires. Revisit
 * once role/account management exists.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(SecurityConstants.AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(SecurityConstants.BEARER_PREFIX.length());

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = jwtProvider.extractUsername(jwt);
                Long userId = jwtProvider.extractUserId(jwt);
                List<String> roleAuthorities = jwtProvider.extractRoles(jwt);

                List<GrantedAuthority> authorities = roleAuthorities.stream()
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                        .toList();

                CmsUserDetails userDetails = new CmsUserDetails(userId, username, null, true, authorities);

                if (jwtProvider.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            log.warn("Could not authenticate from JWT: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
