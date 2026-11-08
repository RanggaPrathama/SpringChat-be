package com.springchat.springchat_chat.modules.auth.service;

import com.springchat.springchat_chat.modules.auth.dto.request.LoginRequest;
import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

/** Account registration and session lifecycle operations. */
public interface AuthService {
    UserResponse registerUser(@Valid @NotNull(message = "Lengkapi data yang diperlukan.") CreateUserRequest request, String remoteAddress);

    Optional<IssuedTokens> loginWithEmail(@Valid @NotNull(message = "Lengkapi data yang diperlukan.") LoginRequest request);

    /** Rejections return empty so any session revocation can commit before HTTP 401. */
    Optional<IssuedTokens> refreshSession(String rawToken);

    void logoutSession(String rawToken);
}
