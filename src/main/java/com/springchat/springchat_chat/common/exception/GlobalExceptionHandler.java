package com.springchat.springchat_chat.common.exception;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.springchat.springchat_chat.common.response.ApiResponse;

/**
 * Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions
 * (405, 415, 404 no-handler, type mismatch, malformed body, ...) keep their
 * correct HTTP status instead of falling into the catch-all 500 handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "CONFLICT");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "TOO_MANY_REQUESTS");
    }

    /** A unique-constraint race is a conflict; other integrity failures are server defects. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        if (isUniqueViolation(ex)) {
            log.warn("Unique constraint violation");
            return build(HttpStatus.CONFLICT, "Resource already exists", "CONFLICT");
        }
        log.error("Database integrity failure", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_SERVER_ERROR");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected application error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_SERVER_ERROR");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.error("Validation failed", errors));
    }

    /** Re-wraps every other Spring MVC exception in the standard {@link ApiResponse} body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : "Request failed";
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status != null ? status.name() : String.valueOf(statusCode.value());
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiResponse.error(message, error));
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message, Object error) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, error));
    }

    private boolean isUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                for (SQLException current = sqlException; current != null; current = current.getNextException()) {
                    if ("23505".equals(current.getSQLState())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
