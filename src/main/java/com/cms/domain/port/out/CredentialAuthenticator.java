package com.cms.domain.port.out;

public interface CredentialAuthenticator {

    void authenticate(String email, String rawPassword);
}
