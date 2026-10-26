package com.springchat.springchat_chat.modules.chat.dto;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.springchat.springchat_chat.utils.Sanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 5000) String content,
        @NotNull @JsonAlias("clientMessageId") UUID idempotencyKey) {

    public SendMessageRequest {
        content = Sanitizer.sanitize(content);
    }
}
