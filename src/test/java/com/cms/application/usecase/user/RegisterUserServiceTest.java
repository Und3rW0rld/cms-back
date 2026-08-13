package com.cms.application.usecase.user;

import com.cms.domain.exception.EmailAlreadyExistsException;
import com.cms.domain.model.user.Role;
import com.cms.domain.model.user.User;
import com.cms.domain.port.out.CredentialRepository;
import com.cms.domain.port.out.RoleRepository;
import com.cms.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        service = new RegisterUserService(userRepository, credentialRepository, roleRepository, passwordEncoder);
    }

    @Test
    void shouldRegisterNewUserWithDefaultEditorRole() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");

        Instant now = Instant.now();
        User savedWithoutRoles = new User(1L, "new@example.com", "New User", true, Set.of(), now, now);
        when(userRepository.save(any(User.class))).thenReturn(savedWithoutRoles);

        User result = service.register("new@example.com", "plaintext", "New User");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.roles()).containsExactly(Role.EDITOR);

        verify(credentialRepository).save(1L, "hashed");
        verify(roleRepository).assignRole(1L, Role.EDITOR);
    }

    @Test
    void shouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("taken@example.com", "plaintext", "Someone"))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("taken@example.com");

        verify(userRepository, never()).save(any());
        verify(credentialRepository, never()).save(anyLong(), anyString());
        verify(roleRepository, never()).assignRole(any(), eq(Role.EDITOR));
    }
}
