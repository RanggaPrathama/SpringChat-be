package com.springchat.springchat_chat.modules.users.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.springchat.springchat_chat.common.exception.GlobalExceptionHandler;
import com.springchat.springchat_chat.config.SecurityConfig;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;
import com.springchat.springchat_chat.modules.users.service.UserService;

@WebMvcTest(UserController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class UserControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void profileReturnsEmailForCurrentUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getOwnProfile(userId)).thenReturn(
                new UserResponse(userId, "alice", "alice@example.com", null));

        mockMvc.perform(get("/api/v1/users/me").with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    void patchProfileRequiresCsrfAndAcceptsOnlyProfileFields() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.updateOwnProfile(any(), any())).thenReturn(
                new UserResponse(userId, "alicenew", "alice@example.com", null));

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.subject(userId.toString())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"AliceNew\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alicenew"))
                .andExpect(jsonPath("$.data.displayName").doesNotExist());
    }

    @Test
    void searchReturnsUserFieldsIncludingEmailAndRequiresAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        when(userService.searchUsers("al", userId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(new UserResponse(otherId, "alex", "alex@example.com", null)),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/users").param("search", "al")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(otherId.toString()))
                .andExpect(jsonPath("$.data[0].email").value("alex@example.com"));
    }
}
