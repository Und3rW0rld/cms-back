package com.cms.adapters.in.security;

import com.cms.domain.port.out.CredentialAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringSecurityCredentialAuthenticator implements CredentialAuthenticator {

    private final AuthenticationManager authenticationManager;

    @Override
    public void authenticate(String email, String rawPassword) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));
    }
}
