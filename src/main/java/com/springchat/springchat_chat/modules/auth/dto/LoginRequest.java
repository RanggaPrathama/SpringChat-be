package com.springchat.springchat_chat.modules.auth.dto;

import com.springchat.springchat_chat.utils.Sanitizer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password) {

    public LoginRequest {
        email = Sanitizer.normalize(email);
    }
}
