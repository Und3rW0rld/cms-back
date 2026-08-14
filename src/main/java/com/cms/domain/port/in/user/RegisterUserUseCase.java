package com.cms.domain.port.in.user;

import com.cms.domain.model.user.User;

/** Registers a new user with a default EDITOR role. */
public interface RegisterUserUseCase {

    User register(RegisterUserCommand command);
}
