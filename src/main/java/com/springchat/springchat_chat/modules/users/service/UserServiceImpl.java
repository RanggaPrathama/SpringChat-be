package com.springchat.springchat_chat.modules.users.service;

import com.springchat.springchat_chat.common.exception.DuplicateResourceException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.request.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.mapper.UserMapper;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String query, UUID currentUserId, Pageable pageable) {
        String escapedQuery = query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        log.debug("Searching users requesterId={} page={}", currentUserId, pageable.getPageNumber());
        return userRepository.searchDirectory(escapedQuery, currentUserId, pageable).map(UserMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        return UserMapper.toResponse(requireUserById(userId));
    }

    @Override
    @Transactional
    public UserResponse registerUser(CreateUserRequest request) {
        String username = request.getUsername();
        String email = request.getEmail();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.debug("Registration rejected: duplicate username");
            throw new DuplicateResourceException("Nama pengguna sudah digunakan. Pilih nama lain.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.debug("Registration rejected: duplicate email");
            throw new DuplicateResourceException("Email sudah terdaftar. Silakan masuk atau gunakan email lain.");
        }

        User user = UserMapper.toEntity(request, passwordEncoder.encode(request.getPassword()));
        User saved = userRepository.save(user);
        log.debug("User insert prepared userId={}", saved.getId());
        return UserMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getOwnProfile(UUID userId) {
        return UserMapper.toResponse(requireUserById(userId));
    }

    @Override
    @Transactional
    public UserResponse updateOwnProfile(UUID userId, ProfileUpdateRequest request) {
        User user = requireUserById(userId);
        if (request.getUsername() != null) {
            String username = request.getUsername();
            if (!username.equals(user.getUsername()) && userRepository.existsByUsernameIgnoreCase(username)) {
                throw new DuplicateResourceException("Nama pengguna sudah digunakan. Pilih nama lain.");
            }

        }
        UserMapper.applyProfile(user, request);
        log.debug("Profile update prepared userId={}", userId);
        return UserMapper.toResponse(user);
    }

    private User requireUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna tidak ditemukan atau sudah tidak tersedia."));
    }

}
