package com.cms.adapters.in.web.exception;

public final class ErrorCodes {

    private ErrorCodes() {
        throw new AssertionError("Utility class - cannot be instantiated");
    }

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String PRECONDITION_FAILED = "PRECONDITION_FAILED";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
}
