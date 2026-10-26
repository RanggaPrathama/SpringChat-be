package com.springchat.springchat_chat.modules.chat.controller.v1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.springchat.springchat_chat.common.exception.DuplicateResourceException;
import com.springchat.springchat_chat.common.exception.GlobalExceptionHandler;
import com.springchat.springchat_chat.config.SecurityConfig;
import com.springchat.springchat_chat.modules.chat.dto.MessageResponse;
import com.springchat.springchat_chat.modules.chat.dto.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.service.MessageService;

@WebMvcTest(MessageController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class MessageControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private MessageService messageService;
    private final UUID conversationId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();

    @Test
    void canonicalKeyCreatesMessageWithoutDeduplicationFieldsInResponse() throws Exception {
        var response = new MessageResponse(UUID.randomUUID(), conversationId, senderId, "Hello", Instant.now());
        when(messageService.sendMessage(conversationId, senderId, new SendMessageRequest("Hello", key)))
                .thenReturn(new MessageService.SendResult(response, true));
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"" + key + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()))
                .andExpect(jsonPath("$.data.content").value("Hello"))
                .andExpect(jsonPath("$.data.clientMessageId").doesNotExist())
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist());
    }

    @Test
    void legacyClientMessageIdIsAcceptedAsAnAlias() throws Exception {
        var response = new MessageResponse(UUID.randomUUID(), conversationId, senderId, "Hello", Instant.now());
        when(messageService.sendMessage(conversationId, senderId, new SendMessageRequest("Hello", key)))
                .thenReturn(new MessageService.SendResult(response, true));
        send("{\"content\":\"Hello\",\"clientMessageId\":\"" + key + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void replayReturnsExistingMessageWithOkStatus() throws Exception {
        var response = new MessageResponse(UUID.randomUUID(), conversationId, senderId, "Hello", Instant.now());
        when(messageService.sendMessage(any(), any(), any())).thenReturn(new MessageService.SendResult(response, false));
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"" + key + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(response.id().toString()));
    }

    @Test
    void missingKeyIsRejectedBeforeServiceCall() throws Exception {
        send("{\"content\":\"Hello\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(messageService);
    }

    @Test
    void invalidUuidIsRejectedBeforeServiceCall() throws Exception {
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"invalid\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(messageService);
    }

    @Test
    void mismatchedRequestReturnsConflict() throws Exception {
        when(messageService.sendMessage(any(), any(), any()))
                .thenThrow(new DuplicateResourceException("idempotencyKey was already used with a different request"));
        send("{\"content\":\"Hello\",\"idempotencyKey\":\"" + key + "\"}")
                .andExpect(status().isConflict());
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages", conversationId)
                .with(jwt().jwt(token -> token.subject(senderId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
