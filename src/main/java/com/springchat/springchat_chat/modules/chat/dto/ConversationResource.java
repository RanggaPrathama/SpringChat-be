package com.springchat.springchat_chat.modules.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.springchat.springchat_chat.modules.users.dto.UserResponse;

public record ConversationResource(UUID id, String type, UserResponse otherUser, Instant createdAt,
        String name, List<UserResponse> members) {
}
