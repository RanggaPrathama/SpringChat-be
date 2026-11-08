package com.springchat.springchat_chat.modules.chat.service;

import com.springchat.springchat_chat.common.response.CreationResult;
import com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.MarkReadRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationDetailResponse;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationSummaryResponse;
import com.springchat.springchat_chat.common.response.PaginatedResult;
import com.springchat.springchat_chat.modules.chat.dto.response.MessageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Conversation and message use cases exposed by the chat module. */
public interface ChatService {
    CreationResult<ConversationDetailResponse> createConversation(UUID currentUserId, @Valid @NotNull(message = "Lengkapi data yang diperlukan.") CreateConversationRequest request);

    Page<ConversationSummaryResponse> listConversations(UUID userId, Pageable pageable);

    ConversationDetailResponse getConversationById(UUID userId, UUID conversationId);

    CreationResult<MessageResponse> sendMessage(UUID conversationId, UUID senderId, @Valid @NotNull(message = "Lengkapi data yang diperlukan.") SendMessageRequest request);

    PaginatedResult<MessageResponse> getMessageHistory(UUID conversationId, UUID userId, String cursor, @Min(value = 1, message = "Jumlah pesan yang dimuat minimal 1.") @Max(value = 100, message = "Muat maksimal 100 pesan sekaligus.") int limit);

    void markConversationRead(UUID conversationId, UUID userId, @Valid @NotNull(message = "Lengkapi data yang diperlukan.") MarkReadRequest request);
}
