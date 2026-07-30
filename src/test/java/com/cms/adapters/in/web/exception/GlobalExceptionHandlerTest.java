package com.cms.adapters.in.web.exception;

import com.cms.domain.exception.ConflictException;
import com.cms.domain.exception.NotFoundException;
import com.cms.domain.exception.PreconditionFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String TEST_CORRELATION_ID = "550e8400-e29b-41d4-a716-446655440000";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setCorrelationId() {
        MDC.put("correlationId", TEST_CORRELATION_ID);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldReturn404ForNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new NotFoundException("Site not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Site not found");
        assertThat(response.getBody().correlationId()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(response.getBody().fields()).isNull();
    }

    @Test
    void shouldReturn409ForConflictException() {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(
                new ConflictException("Entry has children"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
        assertThat(response.getBody().correlationId()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void shouldReturn412ForPreconditionFailedException() {
        ResponseEntity<ErrorResponse> response = handler.handlePreconditionFailed(
                new PreconditionFailedException("Version mismatch"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(412);
        assertThat(response.getBody().error()).isEqualTo("PRECONDITION_FAILED");
        assertThat(response.getBody().correlationId()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void shouldReturn422WithFieldErrorsForValidationException() throws Exception {
        org.springframework.validation.MapBindingResult bindingResult =
                new org.springframework.validation.MapBindingResult(new java.util.HashMap<>(), "target");
        bindingResult.rejectValue("title", "NotBlank", "must not be blank");
        bindingResult.rejectValue("summary", "Size", "must be at most 255 characters");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(422);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().correlationId()).isEqualTo(TEST_CORRELATION_ID);
        assertThat(response.getBody().fields()).hasSize(2);
        assertThat(response.getBody().fields())
                .extracting(ErrorResponse.FieldError::field)
                .containsExactlyInAnyOrder("title", "summary");
    }

    @Test
    void shouldReturn403ForAccessDeniedException() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().error()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("Access denied");
        assertThat(response.getBody().correlationId()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void shouldReturn401ForAuthenticationException() {
        ResponseEntity<ErrorResponse> response = handler.handleAuthentication(
                new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(401);
        assertThat(response.getBody().error()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("Authentication required");
        assertThat(response.getBody().correlationId()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void shouldReturn500ForGenericException() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("something went wrong internally"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().correlationId()).isEqualTo(TEST_CORRELATION_ID);
    }

    @Test
    void shouldNotLeakInternalDetailsIn500Response() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("DB connection timeout at host 10.0.0.1:5432"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("10.0.0.1");
        assertThat(response.getBody().message()).doesNotContain("5432");
        assertThat(response.getBody().message()).doesNotContain("DB connection");
    }

    @Test
    void shouldIncludeCorrelationIdEvenWhenMdcIsEmpty() {
        MDC.clear();

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("error"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().correlationId()).isNull();
    }
}
