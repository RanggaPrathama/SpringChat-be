package com.springchat.springchat_chat.common.exception;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void frameworkDetailsAreNotReturnedToUsers() {
        var response = handler.handleExceptionInternal(new IllegalArgumentException("internal detail"),
                org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "java.lang.UUID rejected value secret"),
                org.springframework.http.HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST,
                new org.springframework.web.context.request.ServletWebRequest(new org.springframework.mock.web.MockHttpServletRequest()));
        var body = (com.springchat.springchat_chat.common.response.ApiResponse<?>) response.getBody();
        assertThat(body.getMessage()).isEqualTo("Data yang dikirim belum dapat diproses. Periksa kembali isian Anda.");
        assertThat(body.toString()).doesNotContain("java.lang", "secret", "internal detail");
        assertThat(body.getError()).isEqualTo("BAD_REQUEST");
    }

    static class RequestConsumer {
        public void accept(@jakarta.validation.Valid com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest request) {}
    }

    @Test
    void serviceValidationReturnsFieldNamesWithoutJavaMethodPaths() throws Exception {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var method = RequestConsumer.class.getMethod("accept",
                    com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest.class);
            var request = new com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest(
                    com.springchat.springchat_chat.modules.chat.enums.ConversationType.DIRECT, null,
                    java.util.List.of(java.util.UUID.randomUUID(), java.util.UUID.randomUUID()));
            var violations = factory.getValidator().forExecutables().validateParameters(new RequestConsumer(), method,
                    new Object[] {request});
            var response = handler.handleConstraintViolation(new jakarta.validation.ConstraintViolationException(violations));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getError()).isEqualTo(java.util.Map.of(
                    "memberIds", "Pilih satu orang untuk memulai percakapan pribadi."));
        }
    }

    @Test
    void uniqueConstraintViolationReturnsConflict() {
        var response = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "database constraint", new SQLException("duplicate", "23505")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("CONFLICT");
    }

    @Test
    void otherIntegrityViolationReturnsInternalServerError() {
        var response = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "database constraint", new SQLException("foreign key", "23503")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
