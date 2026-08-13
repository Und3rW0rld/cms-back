package com.cms.domain.port.in.user;

import com.cms.domain.model.user.User;

public interface RegisterUserUseCase {

    User register(String email, String rawPassword, String name);
}
