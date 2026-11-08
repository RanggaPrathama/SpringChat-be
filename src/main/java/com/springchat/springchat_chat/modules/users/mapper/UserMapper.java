package com.springchat.springchat_chat.modules.users.mapper;

import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.request.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import com.springchat.springchat_chat.modules.users.entity.User;
public final class UserMapper {
    private UserMapper() {}
    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl());
    }
    public static User toEntity(CreateUserRequest request, String encodedPassword) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(encodedPassword);
        return user;
    }

    public static void applyProfile(User user, ProfileUpdateRequest request) {
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl().isEmpty() ? null : request.getAvatarUrl());
    }
}
