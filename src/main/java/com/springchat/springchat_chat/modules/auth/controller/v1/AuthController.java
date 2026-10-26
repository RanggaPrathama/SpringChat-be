package com.springchat.springchat_chat.modules.auth.controller.v1;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.springchat.springchat_chat.common.response.ApiResponse;
import com.springchat.springchat_chat.modules.auth.dto.AccessTokenResponse;
import com.springchat.springchat_chat.modules.auth.dto.LoginRequest;
import com.springchat.springchat_chat.modules.auth.service.AuthService;
import com.springchat.springchat_chat.modules.users.dto.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String REFRESH_COOKIE = "SPRINGCHAT_REFRESH";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;

    @Value("${springchat.security.cookie-secure:false}")
    private boolean secureCookie;

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfPayload>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(ApiResponse.ok("CSRF token retrieved",
                new CsrfPayload(csrfToken.getHeaderName(), csrfToken.getToken())));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        UserResponse user = authService.registerUser(request, httpRequest.getRemoteAddr());
        log.info("User registered userId={}", user.id());
        return ResponseEntity.status(201).body(ApiResponse.ok("User registered successfully", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        var issued = authService.loginWithEmail(request);
        if (issued.isEmpty()) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                    .body(ApiResponse.<AccessTokenResponse>error("Invalid credentials", "UNAUTHORIZED"));
        }
        AuthService.IssuedTokens tokens = issued.get();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), tokens.refreshExpiresIn()).toString())
                .body(ApiResponse.ok("Signed in successfully", tokens.access()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        var issued = authService.refreshSession(refreshToken);
        if (issued.isEmpty()) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                    .body(ApiResponse.<AccessTokenResponse>error("Refresh session is invalid", "UNAUTHORIZED"));
        }
        AuthService.IssuedTokens tokens = issued.get();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), tokens.refreshExpiresIn()).toString())
                .body(ApiResponse.ok("Session refreshed successfully", tokens.access()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logoutSession(refreshToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .body(ApiResponse.ok("Signed out successfully", null));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    public record CsrfPayload(String headerName, String token) {}
}
