package com.springchat.springchat_chat.modules.chat.dto;

import java.time.Instant;
import java.util.UUID;

import com.springchat.springchat_chat.modules.users.dto.UserResponse;

public record ConversationResponse(UUID id, String type, UserResponse otherUser,
        MessageResponse lastMessage, long unreadCount, Instant lastActivityAt, String name, long memberCount) {
}
