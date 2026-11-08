package com.springchat.springchat_chat.modules.users.service;

import com.springchat.springchat_chat.common.exception.DuplicateResourceException;
import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.request.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;
import com.springchat.springchat_chat.modules.users.service.UserService;
import jakarta.validation.Validation;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserServiceImpl userService;

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

        var user = userService.registerUser(new CreateUserRequest(" Alice ", " ALICE@example.com ", "password123"));

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        verify(passwordEncoder).encode("password123");
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

        assertThat(response.getUsername()).isEqualTo("alicenew");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
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
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateProfileRejectsEmptyPatch() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new ProfileUpdateRequest(null, null))).isNotEmpty();
        }
    }

    @Test
    void searchRejectsShortQueryAndExcludesCurrentUser() throws Exception {
        UUID currentUserId = UUID.randomUUID();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().forExecutables().validateParameters(userService,
                    UserService.class.getMethod("searchUsers",
                    String.class, UUID.class, org.springframework.data.domain.Pageable.class),
                    new Object[] {"a", currentUserId, PageRequest.of(0, 20)})).isNotEmpty();
        }

        when(userRepository.searchDirectory("ali", currentUserId, PageRequest.of(0, 20)))
                .thenReturn(Page.empty());
        assertThat(userService.searchUsers("ali", currentUserId, PageRequest.of(0, 20))).isEmpty();
        verify(userRepository).searchDirectory("ali", currentUserId, PageRequest.of(0, 20));
    }
}
