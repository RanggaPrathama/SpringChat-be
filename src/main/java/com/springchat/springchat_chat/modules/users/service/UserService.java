package com.springchat.springchat_chat.modules.users.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.DuplicateResourceException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.modules.users.dto.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String query, UUID currentUserId, Pageable pageable) {
        if (query == null || query.length() < 2) {
            throw new BadRequestException("Search query must contain at least 2 characters");
        }
        String escapedQuery = query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        log.debug("Searching users requesterId={} page={}", currentUserId, pageable.getPageNumber());
        return userRepository.searchDirectory(escapedQuery, currentUserId, pageable).map(this::toResponse);
    }


    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        return toResponse(requireUserById(userId));
    }

    @Transactional
    public User registerUser(CreateUserRequest request) {
        String username = request.username();
        String email = request.email();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.debug("Registration rejected: duplicate username");
            throw new DuplicateResourceException("Username already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.debug("Registration rejected: duplicate email");
            throw new DuplicateResourceException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        log.debug("User insert prepared userId={}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<User> findActiveUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(User::isEnabled);
    }

    @Transactional(readOnly = true)
    public UserResponse getOwnProfile(UUID userId) {
        return toResponse(requireUserById(userId));
    }

    @Transactional
    public UserResponse updateOwnProfile(UUID userId, ProfileUpdateRequest request) {
        if (request.username() == null && request.avatarUrl() == null) {
            throw new BadRequestException("At least one profile field must be provided");
        }
        User user = requireUserById(userId);
        if (request.username() != null) {
            String username = request.username();
            if (username.length() < 3 || username.length() > 50) {
                throw new BadRequestException("username must contain between 3 and 50 characters");
            }
            if (!username.equals(user.getUsername()) && userRepository.existsByUsernameIgnoreCase(username)) {
                throw new DuplicateResourceException("Username already exists");
            }
            user.setUsername(username);
        }
        if (request.avatarUrl() != null) {
            String avatarUrl = request.avatarUrl();
            user.setAvatarUrl(avatarUrl.isEmpty() ? null : avatarUrl);
        }
        log.debug("Profile update prepared userId={}", userId);
        return toResponse(user);
    }


    private User requireUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl());
    }
}
