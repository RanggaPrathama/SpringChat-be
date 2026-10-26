package com.springchat.springchat_chat.modules.chat.dto;

import java.util.List;
import java.util.UUID;

import com.springchat.springchat_chat.utils.Sanitizer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @NotNull ConversationType type,
        @Size(max = 100) String name,
        @NotNull List<@NotNull UUID> memberIds) {

    public CreateConversationRequest {
        name = Sanitizer.trim(Sanitizer.sanitize(name));
    }
}
