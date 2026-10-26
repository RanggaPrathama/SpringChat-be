package com.springchat.springchat_chat.modules.chat.controller.v1;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springchat.springchat_chat.common.response.ApiResponse;
import com.springchat.springchat_chat.modules.chat.dto.MarkReadRequest;
import com.springchat.springchat_chat.modules.chat.dto.MessagePageResponse;
import com.springchat.springchat_chat.modules.chat.dto.MessageResponse;
import com.springchat.springchat_chat.modules.chat.dto.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.service.MessageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<MessagePageResponse>> history(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit) {
        MessagePageResponse page = messageService.getMessageHistory(conversationId,
                UUID.fromString(jwt.getSubject()), cursor, limit);
        return ResponseEntity.ok(ApiResponse.ok("Messages retrieved successfully", page));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendMessageRequest request) {
        MessageService.SendResult result = messageService.sendMessage(conversationId,
                UUID.fromString(jwt.getSubject()), request);
        ResponseEntity.BodyBuilder response = result.created()
                ? ResponseEntity.status(201) : ResponseEntity.ok();
        return response.body(ApiResponse.ok("Message accepted successfully", result.message()));
    }

    @PutMapping("/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MarkReadRequest request) {
        messageService.markConversationRead(conversationId, UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.ok(ApiResponse.ok("Conversation marked as read", null));
    }
}
