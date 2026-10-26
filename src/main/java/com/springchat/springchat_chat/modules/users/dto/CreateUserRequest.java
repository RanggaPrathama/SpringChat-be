package com.springchat.springchat_chat.modules.users.dto;

import com.springchat.springchat_chat.utils.Sanitizer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,

        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank @Size(min = 8, max = 72) String password) {

    public CreateUserRequest {
        username = Sanitizer.normalize(Sanitizer.sanitize(username));
        email = Sanitizer.normalize(email);
    }
}
