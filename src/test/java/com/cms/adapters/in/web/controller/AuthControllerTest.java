package com.cms.adapters.in.web.controller;

import com.cms.adapters.in.web.dto.request.LoginRequestDTO;
import com.cms.adapters.in.web.dto.request.RegisterRequestDTO;
import com.cms.adapters.in.web.dto.response.AuthResponseDTO;
import com.cms.domain.model.user.AuthToken;
import com.cms.domain.model.user.Role;
import com.cms.domain.model.user.User;
import com.cms.domain.port.in.user.AuthenticateUserCommand;
import com.cms.domain.port.in.user.AuthenticateUserUseCase;
import com.cms.domain.port.in.user.RegisterUserCommand;
import com.cms.domain.port.in.user.RegisterUserUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private RegisterUserUseCase registerUserUseCase;

    @Mock
    private AuthenticateUserUseCase authenticateUserUseCase;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(registerUserUseCase, authenticateUserUseCase);
    }

    @Test
    void shouldReturn201WithTokenOnRegister() {
        RegisterRequestDTO request = new RegisterRequestDTO("new@example.com", "plaintext123", "New User");
        RegisterUserCommand registerCommand = new RegisterUserCommand("new@example.com", "plaintext123", "New User");
        AuthenticateUserCommand authenticateCommand = new AuthenticateUserCommand("new@example.com", "plaintext123");

        Instant now = Instant.now();
        User registered = new User(1L, "new@example.com", "New User", true, Set.of(Role.EDITOR), now, now);
        AuthToken token = new AuthToken("jwt-value", 86_400_000L);

        when(registerUserUseCase.register(registerCommand)).thenReturn(registered);
        when(authenticateUserUseCase.authenticate(authenticateCommand)).thenReturn(token);

        ResponseEntity<AuthResponseDTO> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isEqualTo("jwt-value");
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().expiresIn()).isEqualTo(86_400_000L);

        InOrder order = inOrder(registerUserUseCase, authenticateUserUseCase);
        order.verify(registerUserUseCase).register(registerCommand);
        order.verify(authenticateUserUseCase).authenticate(authenticateCommand);
    }

    @Test
    void shouldReturn200WithTokenOnLogin() {
        LoginRequestDTO request = new LoginRequestDTO("user@example.com", "plaintext123");
        AuthenticateUserCommand command = new AuthenticateUserCommand("user@example.com", "plaintext123");
        AuthToken token = new AuthToken("jwt-value", 86_400_000L);

        when(authenticateUserUseCase.authenticate(command)).thenReturn(token);

        ResponseEntity<AuthResponseDTO> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isEqualTo("jwt-value");
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
    }
}
