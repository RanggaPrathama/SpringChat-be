package com.springchat.springchat_chat.modules.chat.controller.v1;

import com.springchat.springchat_chat.common.exception.DuplicateResourceException;
import com.springchat.springchat_chat.common.exception.GlobalExceptionHandler;
import com.springchat.springchat_chat.common.response.CreationResult;
import com.springchat.springchat_chat.config.SecurityConfig;
import com.springchat.springchat_chat.modules.chat.dto.request.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.dto.response.MessageResponse;
import com.springchat.springchat_chat.modules.chat.service.ChatService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class MessageControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ChatService chatService;
    private final UUID conversationId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();

    @Test
    void firstHistoryPageUsesGlobalCursorMetadataWithoutPageNumbers() throws Exception {
        var message = new MessageResponse(UUID.randomUUID(), conversationId, senderId, "Hello", Instant.now());
        when(chatService.getMessageHistory(conversationId, senderId, null, 30)).thenReturn(
                new com.springchat.springchat_chat.common.response.PaginatedResult<>(java.util.List.of(message),
                        com.springchat.springchat_chat.common.response.PaginationMeta.cursor(30, true, "next-position")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/conversations/" + conversationId + "/messages")
                        .with(jwt().jwt(token -> token.subject(senderId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(message.getId().toString()))
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andExpect(jsonPath("$.pagination.size").value(30))
                .andExpect(jsonPath("$.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.pagination.nextCursor").value("next-position"))
                .andExpect(jsonPath("$.pagination.page").doesNotExist())
                .andExpect(jsonPath("$.pagination.totalElements").doesNotExist())
                .andExpect(jsonPath("$.pagination.totalPages").doesNotExist())
                .andExpect(jsonPath("$.pagination.hasPrevious").doesNotExist());
    }

    @Test
    void emptyFinalHistoryPageOmitsNextCursor() throws Exception {
        when(chatService.getMessageHistory(conversationId, senderId, "position", 10)).thenReturn(
                new com.springchat.springchat_chat.common.response.PaginatedResult<>(java.util.List.of(),
                        com.springchat.springchat_chat.common.response.PaginationMeta.cursor(10, false, null)));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/conversations/" + conversationId + "/messages")
                        .param("cursor", "position").param("limit", "10")
                        .with(jwt().jwt(token -> token.subject(senderId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.pagination.size").value(10))
                .andExpect(jsonPath("$.pagination.hasNext").value(false))
                .andExpect(jsonPath("$.pagination.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.pagination.page").doesNotExist());
    }

    @Test
    void canonicalKeyCreatesMessageWithoutDeduplicationFieldsInResponse() throws Exception {
        var response = new MessageResponse(UUID.randomUUID(), conversationId, senderId, "Hello", Instant.now());
        when(chatService.sendMessage(conversationId, senderId, new SendMessageRequest("Hello", key)))
                .thenReturn(new CreationResult<>(response, true));
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"" + key + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.data.content").value("Hello"))
                .andExpect(jsonPath("$.data.created").doesNotExist())
                .andExpect(jsonPath("$.data.clientMessageId").doesNotExist())
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist());
    }

    @Test
    void legacyClientMessageIdIsAcceptedAsAnAlias() throws Exception {
        var response = new MessageResponse(UUID.randomUUID(), conversationId, senderId, "Hello", Instant.now());
        when(chatService.sendMessage(conversationId, senderId, new SendMessageRequest("Hello", key)))
                .thenReturn(new CreationResult<>(response, true));
        send("{\"content\":\"Hello\",\"clientMessageId\":\"" + key + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void replayReturnsExistingMessageWithOkStatus() throws Exception {
        var response = new MessageResponse(UUID.randomUUID(), conversationId, senderId, "Hello", Instant.now());
        when(chatService.sendMessage(any(), any(), any())).thenReturn(new CreationResult<>(response, false));
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"" + key + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").doesNotExist())
                .andExpect(jsonPath("$.data.id").value(response.getId().toString()));
    }

    @Test
    void missingKeyIsRejectedBeforeServiceCall() throws Exception {
        send("{\"content\":\"Hello\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void invalidUuidIsRejectedBeforeServiceCall() throws Exception {
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"invalid\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void mismatchedRequestReturnsConflict() throws Exception {
        when(chatService.sendMessage(any(), any(), any()))
                .thenThrow(new DuplicateResourceException("Pengiriman pesan tidak dapat dilanjutkan. Muat ulang percakapan, lalu kirim kembali."));
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"" + key + "\"}")
                .andExpect(status().isConflict());
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages", conversationId)
                .with(jwt().jwt(token -> token.subject(senderId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
