package com.springchat.springchat_chat.modules.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.modules.users.dto.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    @Test
    void registerNormalizesIdentityAndHashesPassword() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        User user = userService.registerUser(new CreateUserRequest(" Alice ", " ALICE@example.com ", "password123"));

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPassword()).isEqualTo("hashed");
    }

    @Test
    void updateProfileChangesOnlyAllowedFields() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var response = userService.updateOwnProfile(userId,
                new ProfileUpdateRequest("AliceNew", "https://example.com/avatar.png"));

        assertThat(response.username()).isEqualTo("alicenew");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.avatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    void updateProfileRejectsUsernameAlreadyOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCase("bob")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateOwnProfile(userId, new ProfileUpdateRequest("Bob", null)))
                .isInstanceOf(com.springchat.springchat_chat.common.exception.DuplicateResourceException.class);
    }

    @Test
    void updateProfileRejectsEmptyPatch() {
        assertThatThrownBy(() -> userService.updateOwnProfile(UUID.randomUUID(), new ProfileUpdateRequest(null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void searchRejectsShortQueryAndExcludesCurrentUser() {
        UUID currentUserId = UUID.randomUUID();
        assertThatThrownBy(() -> userService.searchUsers("a", currentUserId, PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class);

        when(userRepository.searchDirectory("ali", currentUserId, PageRequest.of(0, 20)))
                .thenReturn(Page.empty());
        assertThat(userService.searchUsers("ali", currentUserId, PageRequest.of(0, 20))).isEmpty();
        verify(userRepository).searchDirectory("ali", currentUserId, PageRequest.of(0, 20));
    }
}
