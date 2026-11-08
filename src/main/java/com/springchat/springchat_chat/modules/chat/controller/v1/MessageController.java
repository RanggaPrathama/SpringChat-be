package com.springchat.springchat_chat.modules.chat.controller.v1;

import com.springchat.springchat_chat.common.response.ApiResponse;
import com.springchat.springchat_chat.common.response.CreationResult;
import com.springchat.springchat_chat.modules.chat.dto.request.MarkReadRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.SendMessageRequest;
import com.springchat.springchat_chat.common.response.PaginatedResult;
import com.springchat.springchat_chat.modules.chat.dto.response.MessageResponse;
import com.springchat.springchat_chat.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}")
@RequiredArgsConstructor
public class MessageController {
    private final ChatService chatService;

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> history(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit) {
        PaginatedResult<MessageResponse> page = chatService.getMessageHistory(conversationId,
                UUID.fromString(jwt.getSubject()), cursor, limit);
        return ResponseEntity.ok(ApiResponse.paginated("Riwayat pesan berhasil dimuat.", page.data(), page.pagination()));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendMessageRequest request) {
        CreationResult<MessageResponse> result = chatService.sendMessage(conversationId,
                UUID.fromString(jwt.getSubject()), request);
        ResponseEntity.BodyBuilder response = result.created()
                ? ResponseEntity.status(201) : ResponseEntity.ok();
        return response.body(ApiResponse.ok("Message accepted successfully", result.data()));
    }

    @PutMapping("/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MarkReadRequest request) {
        chatService.markConversationRead(conversationId, UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.ok(ApiResponse.ok("Conversation marked as read", null));
    }
}
