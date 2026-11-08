package com.springchat.springchat_chat.common.exception;

import com.springchat.springchat_chat.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

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
            return build(HttpStatus.CONFLICT, "Data ini sudah tersedia. Periksa kembali isian Anda.", "CONFLICT");
        }
        log.error("Database integrity failure", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kendala saat memproses permintaan Anda. Silakan coba lagi nanti.", "INTERNAL_SERVER_ERROR");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.putIfAbsent(validationField(v.getPropertyPath()), v.getMessage()));
        return ResponseEntity.badRequest().body(ApiResponse.error("Periksa kembali isian Anda.", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected application error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kendala saat memproses permintaan Anda. Silakan coba lagi nanti.", "INTERNAL_SERVER_ERROR");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(status).headers(headers)
                .body(ApiResponse.error("Periksa kembali isian Anda.", errors));
    }

    /** Re-wraps every other Spring MVC exception in the standard {@link ApiResponse} body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Framework details may contain Java types, parameter names, or rejected values.
        String message = switch (statusCode.value()) {
            case 400 -> "Data yang dikirim belum dapat diproses. Periksa kembali isian Anda.";
            case 401 -> "Silakan masuk untuk melanjutkan.";
            case 403 -> "Anda tidak memiliki izin untuk melakukan tindakan ini.";
            case 404 -> "Halaman atau data yang Anda cari tidak ditemukan.";
            case 405 -> "Tindakan ini tidak tersedia pada halaman yang dipilih.";
            case 413 -> "Data yang dikirim terlalu besar. Kurangi ukurannya lalu coba kembali.";
            case 415 -> "Format data yang dikirim belum didukung.";
            case 429 -> "Terlalu banyak permintaan. Silakan coba lagi nanti.";
            default -> statusCode.is5xxServerError()
                    ? "Terjadi kendala saat memproses permintaan Anda. Silakan coba lagi nanti."
                    : "Permintaan belum dapat diproses. Silakan coba kembali.";
        };
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status != null ? status.name() : String.valueOf(statusCode.value());
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiResponse.error(message, error));
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message, Object error) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, error));
    }

    private String validationField(jakarta.validation.Path path) {
        String field = "request";
        for (jakarta.validation.Path.Node node : path) {
            if (node.getKind() == jakarta.validation.ElementKind.PROPERTY
                    || node.getKind() == jakarta.validation.ElementKind.PARAMETER) {
                field = node.getName();
            }
        }
        return field;
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
