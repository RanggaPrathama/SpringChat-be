package com.springchat.springchat_chat.modules.auth.dto;

import com.springchat.springchat_chat.modules.users.dto.UserResponse;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
