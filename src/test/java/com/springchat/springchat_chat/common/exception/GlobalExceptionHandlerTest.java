package com.springchat.springchat_chat.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uniqueConstraintViolationReturnsConflict() {
        var response = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "database constraint", new SQLException("duplicate", "23505")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("CONFLICT");
    }

    @Test
    void otherIntegrityViolationReturnsInternalServerError() {
        var response = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "database constraint", new SQLException("foreign key", "23503")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
