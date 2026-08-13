package com.cms.application.usecase.user;

import com.cms.domain.model.user.AuthToken;
import com.cms.domain.port.in.user.AuthenticateUserUseCase;
import com.cms.domain.port.out.CredentialAuthenticator;
import com.cms.domain.port.out.TokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticateUserService implements AuthenticateUserUseCase {

    private final CredentialAuthenticator credentialAuthenticator;
    private final TokenIssuer tokenIssuer;

    @Override
    public AuthToken authenticate(String email, String rawPassword) {
        credentialAuthenticator.authenticate(email, rawPassword);
        return tokenIssuer.issueToken(email);
    }
}
