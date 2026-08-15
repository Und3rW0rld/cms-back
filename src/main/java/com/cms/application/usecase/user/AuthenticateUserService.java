package com.cms.application.usecase.user;

import com.cms.domain.model.user.AuthToken;
import com.cms.domain.port.in.user.AuthenticateUserCommand;
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
    public AuthToken authenticate(AuthenticateUserCommand command) {
        credentialAuthenticator.authenticate(command.email(), command.rawPassword());
        return tokenIssuer.issueToken(command.email());
    }
}
