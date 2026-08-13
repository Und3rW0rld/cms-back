package com.cms.domain.exception;

public class EmailAlreadyExistsException extends ConflictException {

    public EmailAlreadyExistsException(String email) {
        super("A user with email '" + email + "' already exists");
    }
}
