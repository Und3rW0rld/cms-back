package com.cms.adapters.in.web.exception;

import com.cms.adapters.config.filter.CorrelationIdFilter;
import com.cms.domain.exception.ConflictException;
import com.cms.domain.exception.NotFoundException;
import com.cms.domain.exception.PreconditionFailedException;
import com.cms.domain.shared.ContentTooLargeException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDTO.of(404, ErrorCodes.NOT_FOUND, ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponseDTO.of(409, ErrorCodes.CONFLICT, ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(PreconditionFailedException.class)
    public ResponseEntity<ErrorResponseDTO> handlePreconditionFailed(PreconditionFailedException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(ErrorResponseDTO.of(412, ErrorCodes.PRECONDITION_FAILED, ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(ContentTooLargeException.class)
    public ResponseEntity<ErrorResponseDTO> handleContentTooLarge(ContentTooLargeException ex) {
        // ContentTooLargeException indicates domain-level validation failed (content > 1MB)
        // Return the specific message from the domain exception
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponseDTO.of(413, ErrorCodes.PAYLOAD_TOO_LARGE, ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        // MaxUploadSizeExceededException indicates transport/HTTP layer limit was exceeded (> 2MB multipart upload)
        // Return a generic message since the actual size is not always available
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponseDTO.of(413, ErrorCodes.PAYLOAD_TOO_LARGE,
                        "Request body exceeds the maximum allowed size (2MB)", correlationId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponseDTO.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponseDTO.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponseDTO.withFields(422, ErrorCodes.VALIDATION_ERROR,
                        "Request validation failed", correlationId(), fields));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponseDTO.of(403, ErrorCodes.FORBIDDEN, "Access denied", correlationId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponseDTO.of(401, ErrorCodes.UNAUTHORIZED, "Authentication failed", correlationId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDTO.of(500, ErrorCodes.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred", correlationId()));
    }

    private String correlationId() {
        return MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    }
}
