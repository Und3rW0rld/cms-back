package com.cms.domain.shared;

public class ContentTooLargeException extends RuntimeException {

    public ContentTooLargeException(String message) {
        super(message);
    }
}
