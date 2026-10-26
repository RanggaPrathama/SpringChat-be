package com.springchat.springchat_chat.modules.chat.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.springchat.springchat_chat.common.exception.GlobalExceptionHandler;
import com.springchat.springchat_chat.config.SecurityConfig;
import com.springchat.springchat_chat.modules.chat.dto.ConversationResource;
import com.springchat.springchat_chat.modules.chat.dto.ConversationType;
import com.springchat.springchat_chat.modules.chat.dto.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.service.ConversationService;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;

@WebMvcTest(ConversationController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class ConversationControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ConversationService conversationService;

    @Test
    void conversationCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(conversationService);
    }

    @Test
    void groupCreationReturnsNameAndMembers() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carolId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var members = List.of(new UserResponse(creatorId, "alice", "alice@example.com", null),
                new UserResponse(bobId, "bob", "bob@example.com", null),
                new UserResponse(carolId, "carol", "carol@example.com", null));
        var request = new CreateConversationRequest(ConversationType.GROUP, "Study group", List.of(bobId, carolId));
        when(conversationService.createConversation(eq(creatorId), eq(request)))
                .thenReturn(new ConversationService.CreateResult(
                        new ConversationResource(conversationId, "GROUP", null, Instant.now(), "Study group", members),
                        true));

        mockMvc.perform(post("/api/v1/conversations")
                        .with(jwt().jwt(token -> token.subject(creatorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"GROUP","name":"Study group","memberIds":["%s","%s"]}
                                """.formatted(bobId, carolId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.data.type").value("GROUP"))
                .andExpect(jsonPath("$.data.name").value("Study group"))
                .andExpect(jsonPath("$.data.members.length()").value(3))
                .andExpect(jsonPath("$.data.members[1].email").value("bob@example.com"))
                .andExpect(jsonPath("$.data.otherUser").isEmpty());
    }

    @Test
    void conversationCreationRequiresType() throws Exception {
        mockMvc.perform(post("/api/v1/conversations").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(conversationService);
    }

    @Test
    void directCreationUsesUnifiedRequestAndReturnsExistingConversation() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        var other = new UserResponse(memberId, "bob", "bob@example.com", null);
        var resource = new ConversationResource(UUID.randomUUID(), "DIRECT", other, Instant.now(), null,
                List.of(new UserResponse(creatorId, "alice", "alice@example.com", null), other));
        var request = new CreateConversationRequest(ConversationType.DIRECT, null, List.of(memberId));
        when(conversationService.createConversation(eq(creatorId), eq(request)))
                .thenReturn(new ConversationService.CreateResult(resource, false));
        mockMvc.perform(post("/api/v1/conversations")
                        .with(jwt().jwt(token -> token.subject(creatorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIRECT\",\"memberIds\":[\"" + memberId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DIRECT"))
                .andExpect(jsonPath("$.data.otherUser.id").value(memberId.toString()))
                .andExpect(jsonPath("$.data.members.length()").value(2));
    }
}
