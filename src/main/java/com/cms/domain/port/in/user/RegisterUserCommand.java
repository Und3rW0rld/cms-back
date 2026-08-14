package com.cms.domain.port.in.user;

public record RegisterUserCommand(String email, String rawPassword, String name) {

    public AuthenticateUserCommand toAuthenticateCommand() {
        return new AuthenticateUserCommand(email, rawPassword);
    }
}
