package com.cms.adapters.in.web.dto.request;

import com.cms.domain.port.in.user.RegisterUserCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Name is required")
        String name
) {
    public RegisterUserCommand toCommand() {
        return new RegisterUserCommand(email, password, name);
    }
}
