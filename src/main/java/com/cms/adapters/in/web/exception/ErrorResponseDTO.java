package com.cms.adapters.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(
        @Schema(description = "HTTP status code.", example = "404")
        int status,

        @Schema(description = "Machine-readable error code (see ErrorCodes).", example = "NOT_FOUND")
        String error,

        @Schema(description = "Human-readable error message. Never leaks internal details "
                + "(stack traces, DB connection info) on 500 responses.")
        String message,

        @Schema(description = "Request correlation ID for tracing this error through logs. "
                + "Null if unavailable.")
        String correlationId,

        @Schema(description = "When the error occurred.")
        Instant timestamp,

        @Schema(description = "Per-field validation errors. Only present on 422 VALIDATION_ERROR responses.")
        List<FieldError> fields
) {

    @Schema(description = "A single field-level validation failure.")
    public record FieldError(
            @Schema(description = "Name of the invalid field.", example = "title")
            String field,

            @Schema(description = "Why the field failed validation.", example = "must not be blank")
            String message
    ) {}

    public static ErrorResponseDTO of(int status, String error, String message, String correlationId) {
        return new ErrorResponseDTO(status, error, message, correlationId, Instant.now(), null);
    }

    public static ErrorResponseDTO withFields(int status, String error, String message,
                                           String correlationId, List<FieldError> fields) {
        return new ErrorResponseDTO(status, error, message, correlationId, Instant.now(), fields);
    }
}
