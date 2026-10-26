package com.springchat.springchat_chat.modules.auth.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.springchat.springchat_chat.common.exception.GlobalExceptionHandler;
import com.springchat.springchat_chat.config.SecurityConfig;
import com.springchat.springchat_chat.modules.auth.dto.AccessTokenResponse;
import com.springchat.springchat_chat.modules.auth.service.AuthService;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class AuthControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuthService authService;

    @Test
    void registrationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginSetsHttpOnlyRefreshCookieAndNeverReturnsItInJson() throws Exception {
        UUID userId = UUID.randomUUID();
        var access = new AccessTokenResponse("access-token", "Bearer", 900,
                new UserResponse(userId, "alice", "alice@example.com", null));
        when(authService.loginWithEmail(any())).thenReturn(Optional.of(
                new AuthService.IssuedTokens(access, "refresh-secret", Duration.ofDays(7))));

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void invalidLoginUsesGenericUnauthorizedResponse() throws Exception {
        when(authService.loginWithEmail(any())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void usernameIsNotAcceptedAsLoginIdentifier() throws Exception {
        when(authService.loginWithEmail(any())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
}
