package com.springchat.springchat_chat.modules.users.dto;

import com.springchat.springchat_chat.utils.Sanitizer;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(min = 3, max = 50) String username,
        @Size(max = 2048) @Pattern(regexp = "^$|https?://.+", message = "must be an http(s) URL") String avatarUrl) {

    public ProfileUpdateRequest {
        username = Sanitizer.normalize(Sanitizer.sanitize(username));
        avatarUrl = Sanitizer.trim(avatarUrl);
    }
}
