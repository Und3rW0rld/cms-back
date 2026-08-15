package com.cms.adapters.in.web.controller;

import com.cms.adapters.config.SecurityConstants;
import com.cms.adapters.in.web.dto.request.LoginRequestDTO;
import com.cms.adapters.in.web.dto.request.RegisterRequestDTO;
import com.cms.adapters.in.web.dto.response.AuthResponseDTO;
import com.cms.domain.model.user.AuthToken;
import com.cms.domain.port.in.user.AuthenticateUserUseCase;
import com.cms.domain.port.in.user.RegisterUserCommand;
import com.cms.domain.port.in.user.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = SecurityConstants.TAG_AUTH)
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;

    @PostMapping("/register")
    @SecurityRequirements
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        RegisterUserCommand registerCommand = request.toCommand();
        registerUserUseCase.register(registerCommand);

        AuthToken token = authenticateUserUseCase.authenticate(registerCommand.toAuthenticateCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDTO.from(token));
    }

    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        AuthToken token = authenticateUserUseCase.authenticate(request.toCommand());
        return ResponseEntity.ok(AuthResponseDTO.from(token));
    }
}
