package com.cms.application.usecase.user;

import com.cms.domain.model.user.AuthToken;
import com.cms.domain.port.out.CredentialAuthenticator;
import com.cms.domain.port.out.TokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    @Mock
    private CredentialAuthenticator credentialAuthenticator;

    @Mock
    private TokenIssuer tokenIssuer;

    private AuthenticateUserService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticateUserService(credentialAuthenticator, tokenIssuer);
    }

    @Test
    void shouldIssueTokenAfterSuccessfulAuthentication() {
        AuthToken expected = new AuthToken("jwt-value", 86_400_000L);
        when(tokenIssuer.issueToken("user@example.com")).thenReturn(expected);

        AuthToken result = service.authenticate("user@example.com", "correct-password");

        assertThat(result).isEqualTo(expected);

        InOrder order = inOrder(credentialAuthenticator, tokenIssuer);
        order.verify(credentialAuthenticator).authenticate("user@example.com", "correct-password");
        order.verify(tokenIssuer).issueToken("user@example.com");
    }

    @Test
    void shouldNotIssueTokenWhenCredentialsAreInvalid() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(credentialAuthenticator).authenticate("user@example.com", "wrong-password");

        assertThatThrownBy(() -> service.authenticate("user@example.com", "wrong-password"))
                .isInstanceOf(BadCredentialsException.class);

        verify(tokenIssuer, never()).issueToken(anyString());
    }
}
