package com.springchat.springchat_chat.modules.chat.controller.v1;

import com.springchat.springchat_chat.common.response.ApiResponse;
import com.springchat.springchat_chat.common.response.PaginationMeta;
import com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationDetailResponse;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationSummaryResponse;
import com.springchat.springchat_chat.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ConversationSummaryResponse> page = chatService.listConversations(UUID.fromString(jwt.getSubject()), pageable);
        return ResponseEntity.ok(ApiResponse.paginated("Conversations retrieved successfully",
                page.getContent(), PaginationMeta.from(page)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateConversationRequest request) {
        var result = chatService.createConversation(UUID.fromString(jwt.getSubject()), request);
        if (result.created()) {
            log.info("Conversation created conversationId={} type={}", result.data().getId(), request.getType());
        }
        ResponseEntity.BodyBuilder response = result.created()
                ? ResponseEntity.status(201) : ResponseEntity.ok();
        return response.body(ApiResponse.ok("Conversation created or retrieved successfully", result.data()));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> getById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId) {
        return ResponseEntity.ok(ApiResponse.ok("Conversation retrieved successfully",
                chatService.getConversationById(UUID.fromString(jwt.getSubject()), conversationId)));
    }
}
