package com.springchat.springchat_chat;

import com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.enums.ConversationType;
import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {
    @Test
    void settersConstructorsAndJsonNormalizeWithoutExposingPassword() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var request = mapper.readValue("{\"username\":\" Alice \",\"email\":\" ALICE@example.com \",\"password\":\" Password123 \"}", CreateUserRequest.class);
        assertThat(request.getUsername()).isEqualTo("alice");
        assertThat(request.getEmail()).isEqualTo("alice@example.com");
        assertThat(request.getPassword()).isEqualTo(" Password123 ");
        request.setUsername(" Bob ");
        assertThat(request.getUsername()).isEqualTo("bob");
        assertThat(mapper.writeValueAsString(request)).doesNotContain("password");
        assertThat(request.toString()).doesNotContain("Password123");
        assertThat(new CreateUserRequest(" Alice ", " ALICE@example.com ", "password123").getUsername()).isEqualTo("alice");
        UUID key = UUID.randomUUID();
        var message = mapper.readValue("{\"content\":\"<b>Hello</b>\",\"clientMessageId\":\"" + key + "\"}", SendMessageRequest.class);
        assertThat(message.getContent()).isEqualTo("Hello");
        assertThat(message.getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    void crossFieldConstraintsValidateSanitizedConversationInput() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            UUID id = UUID.randomUUID();
            assertThat(validator.validate(new CreateConversationRequest(ConversationType.DIRECT, null, List.of(id)))).isEmpty();
            assertThat(validator.validate(new CreateConversationRequest(ConversationType.DIRECT, "name", List.of(id)))).isNotEmpty();
            assertThat(validator.validate(new CreateConversationRequest(ConversationType.GROUP, "<script>x</script>", List.of(id, UUID.randomUUID())))).isNotEmpty();
            assertThat(validator.validate(new CreateConversationRequest(ConversationType.GROUP, "group", List.of(id, id)))).isNotEmpty();
        }
    }
}
