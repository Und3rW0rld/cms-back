package com.cms.domain.port.in.user;

public record AuthenticateUserCommand(String email, String rawPassword) {
}
