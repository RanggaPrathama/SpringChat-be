package com.springchat.springchat_chat.modules.users.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String avatarUrl) {
}
