package com.cms.adapters.in.web.dto.request;

import com.cms.domain.port.in.user.AuthenticateUserCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
    public AuthenticateUserCommand toCommand() {
        return new AuthenticateUserCommand(email, password);
    }
}
