package com.springchat.springchat_chat.modules.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springchat.springchat_chat.common.exception.TooManyRequestsException;
import com.springchat.springchat_chat.modules.auth.dto.AccessTokenResponse;
import com.springchat.springchat_chat.modules.auth.dto.LoginRequest;
import com.springchat.springchat_chat.modules.auth.entity.AuthSession;
import com.springchat.springchat_chat.modules.auth.entity.RefreshToken;
import com.springchat.springchat_chat.modules.auth.repository.AuthSessionRepository;
import com.springchat.springchat_chat.modules.auth.repository.RefreshTokenRepository;
import com.springchat.springchat_chat.modules.auth.security.AuthRateLimiter;
import com.springchat.springchat_chat.modules.auth.security.JwtTokenService;
import com.springchat.springchat_chat.modules.users.dto.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int MAX_REGISTRATIONS_PER_IP = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final AuthRateLimiter rateLimiter;

    @Transactional
    public UserResponse registerUser(CreateUserRequest request, String remoteAddress) {
        if (!rateLimiter.acquire("register:" + remoteAddress, MAX_REGISTRATIONS_PER_IP,
                REGISTRATION_WINDOW)) {
            log.debug("Registration rate limit exceeded");
            throw new TooManyRequestsException("Registration rate limit exceeded");
        }
        UserResponse registered = toResponse(userService.registerUser(request));
        log.debug("User registration prepared userId={}", registered.id());
        return registered;
    }

    @Transactional
    public Optional<IssuedTokens> loginWithEmail(LoginRequest request) {
        String accountKey = request.email();
        if (!rateLimiter.acquireLoginAttempt(accountKey, MAX_LOGIN_FAILURES, LOGIN_WINDOW)) {
            log.debug("Login rate limit exceeded");
            throw new TooManyRequestsException("Login rate limit exceeded");
        }

        Optional<User> candidate = userService.findActiveUserByEmail(accountKey);
        if (candidate.isEmpty() || !passwordEncoder.matches(request.password(), candidate.get().getPassword())) {
            log.debug("Login rejected: invalid credentials");
            return Optional.empty();
        }

        User user = candidate.get();
        rateLimiter.loginSucceeded(accountKey);
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        AuthSession session = sessionRepository.save(new AuthSession(user, expiresAt));
        String refresh = newRefreshToken();
        refreshTokenRepository.save(new RefreshToken(session, hash(refresh), expiresAt));
        log.debug("Login session prepared sessionId={} userId={}", session.getId(), user.getId());
        return Optional.of(new IssuedTokens(tokenResponse(user), refresh,
                Duration.between(Instant.now(), expiresAt)));
    }

    /** Returns no credentials on rejection; replay revocation is committed before the caller emits 401. */
    @Transactional
    public Optional<IssuedTokens> refreshSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hash(rawToken);
        Optional<UUID> sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }

        // Always lock the session before a token row so refresh and logout serialize consistently.
        AuthSession session = sessionRepository.findByIdForUpdate(sessionId.get()).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(tokenHash).orElse(null);
        if (token == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (token.getConsumedAt() != null) {
            if (session.getRevokedAt() == null) {
                log.warn("Refresh token replay detected sessionId={}", session.getId());
            }
            session.setRevokedAt(now);
            return Optional.empty();
        }
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)
                || !token.getExpiresAt().isAfter(now) || !session.getUser().isEnabled()) {
            session.setRevokedAt(now);
            log.debug("Refresh session rejected sessionId={}", session.getId());
            return Optional.empty();
        }

        token.setConsumedAt(now);
        refreshTokenRepository.saveAndFlush(token);
        User user = session.getUser();
        String nextRawToken = newRefreshToken();
        refreshTokenRepository.save(new RefreshToken(session, hash(nextRawToken), session.getExpiresAt()));
        log.debug("Refresh token rotation prepared sessionId={}", session.getId());
        return Optional.of(new IssuedTokens(tokenResponse(user), nextRawToken,
                Duration.between(now, session.getExpiresAt())));
    }

    @Transactional
    public void logoutSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hash(rawToken);
        refreshTokenRepository.findSessionIdByTokenHash(tokenHash).ifPresent(sessionId ->
                sessionRepository.findByIdForUpdate(sessionId).ifPresent(session -> {
                    Instant now = Instant.now();
                    if (session.getRevokedAt() == null) {
                        session.setRevokedAt(now);
                    }
                    refreshTokenRepository.consumeUnconsumedBySessionId(sessionId, now);
                    log.debug("Session revocation prepared sessionId={}", sessionId);
                }));
    }

    private AccessTokenResponse tokenResponse(User user) {
        return new AccessTokenResponse(jwtTokenService.issue(user.getId()), "Bearer",
                jwtTokenService.accessTtlSeconds(), toResponse(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getAvatarUrl());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record IssuedTokens(AccessTokenResponse access, String refreshToken, Duration refreshExpiresIn) {}
}
