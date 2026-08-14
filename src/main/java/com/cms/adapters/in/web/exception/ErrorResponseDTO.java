package com.cms.adapters.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
        int status,
        String error,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldError> fields
) {

    public record FieldError(String field, String message) {}

    public static ErrorResponseDTO of(int status, String error, String message, String correlationId) {
        return new ErrorResponseDTO(status, error, message, correlationId, Instant.now(), null);
    }

    public static ErrorResponseDTO withFields(int status, String error, String message,
                                           String correlationId, List<FieldError> fields) {
        return new ErrorResponseDTO(status, error, message, correlationId, Instant.now(), fields);
    }
}
