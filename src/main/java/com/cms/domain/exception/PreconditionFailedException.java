package com.cms.domain.exception;

public class PreconditionFailedException extends RuntimeException {

    public PreconditionFailedException(String message) {
        super(message);
    }
}
