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
import java.util.Optional;

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
                buildUserDetails(jwt)
                        .filter(userDetails -> jwtProvider.isTokenValid(jwt, userDetails))
                        .ifPresent(userDetails -> authenticate(userDetails, request));
            }
        } catch (Exception e) {
            log.warn("Could not authenticate from JWT: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private Optional<CmsUserDetails> buildUserDetails(String jwt) {
        String username = jwtProvider.extractUsername(jwt);
        Long userId = jwtProvider.extractUserId(jwt);
        List<String> roleAuthorities = jwtProvider.extractRoles(jwt);

        if (username == null || userId == null || roleAuthorities == null) {
            log.warn("JWT missing required claims (userId/roles) — rejecting");
            return Optional.empty();
        }

        List<GrantedAuthority> authorities = roleAuthorities.stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();

        return Optional.of(new CmsUserDetails(userId, username, null, true, authorities));
    }

    private void authenticate(CmsUserDetails userDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
