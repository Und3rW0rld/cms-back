package com.cms.adapters.in.web.dto.request;

import com.cms.domain.port.in.user.AuthenticateUserCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @Schema(description = "Registered email address.", example = "jane.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "Account password.", example = "at-least-8-characters")
        @NotBlank(message = "Password is required")
        String password
) {
    public AuthenticateUserCommand toCommand() {
        return new AuthenticateUserCommand(email, password);
    }
}
