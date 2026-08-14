package com.cms.application.usecase.user;

import com.cms.domain.exception.EmailAlreadyExistsException;
import com.cms.domain.model.user.Role;
import com.cms.domain.model.user.User;
import com.cms.domain.port.in.user.RegisterUserCommand;
import com.cms.domain.port.in.user.RegisterUserUseCase;
import com.cms.domain.port.out.CredentialRepository;
import com.cms.domain.port.out.RoleRepository;
import com.cms.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new EmailAlreadyExistsException(command.email());
        }

        Instant now = Instant.now();
        User toSave = new User(null, command.email(), command.name(), true, Set.of(), now, now);
        User saved = userRepository.save(toSave);

        credentialRepository.save(saved.id(), passwordEncoder.encode(command.rawPassword()));
        roleRepository.assignRole(saved.id(), Role.EDITOR);

        return new User(saved.id(), saved.email(), saved.name(), saved.enabled(),
                Set.of(Role.EDITOR), saved.createdAt(), saved.updatedAt());
    }
}
