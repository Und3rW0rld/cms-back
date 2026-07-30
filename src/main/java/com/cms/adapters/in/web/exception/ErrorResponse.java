package com.cms.adapters.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldError> fields
) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(int status, String error, String message, String correlationId) {
        return new ErrorResponse(status, error, message, correlationId, Instant.now(), null);
    }

    public static ErrorResponse withFields(int status, String error, String message,
                                           String correlationId, List<FieldError> fields) {
        return new ErrorResponse(status, error, message, correlationId, Instant.now(), fields);
    }
}
