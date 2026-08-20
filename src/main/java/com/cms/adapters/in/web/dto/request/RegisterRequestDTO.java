package com.cms.adapters.in.web.dto.request;

import com.cms.domain.port.in.user.RegisterUserCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @Schema(description = "User's email address. Must be unique across all accounts.",
                example = "jane.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "Account password, minimum 8 characters (NIST SP 800-63B / OWASP guidance). "
                + "Hashed with BCrypt before storage, never logged or persisted "
                + "in plaintext.", example = "at-least-8-characters")
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @Schema(description = "Display name shown in the CMS.", example = "Jane Doe")
        @NotBlank(message = "Name is required")
        String name
) {
    public RegisterUserCommand toCommand() {
        return new RegisterUserCommand(email, password, name);
    }
}
