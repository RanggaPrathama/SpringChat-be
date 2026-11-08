package com.springchat.springchat_chat.modules.users.service;

import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.request.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** User directory, profile, and internal account operations. */
public interface UserService {
    Page<UserResponse> searchUsers(@NotBlank(message = "Masukkan nama pengguna yang ingin dicari.") @Size(min = 2, message = "Ketik setidaknya 2 karakter untuk mencari pengguna.") String query, UUID currentUserId, Pageable pageable);

    UserResponse getUserById(UUID userId);

    UserResponse registerUser(@Valid @NotNull(message = "Lengkapi data yang diperlukan.") CreateUserRequest request);

    UserResponse getOwnProfile(UUID userId);

    UserResponse updateOwnProfile(UUID userId, @Valid @NotNull(message = "Lengkapi data yang diperlukan.") ProfileUpdateRequest request);
}
