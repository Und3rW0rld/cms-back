package com.cms.domain.port.in.user;

import com.cms.domain.model.user.AuthToken;

public interface AuthenticateUserUseCase {

    AuthToken authenticate(AuthenticateUserCommand command);
}
