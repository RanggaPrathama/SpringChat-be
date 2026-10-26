package com.springchat.springchat_chat.modules.chat.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.common.idempotency.IdempotencyService;
import com.springchat.springchat_chat.modules.chat.dto.MarkReadRequest;
import com.springchat.springchat_chat.modules.chat.dto.MessageCreatedEvent;
import com.springchat.springchat_chat.modules.chat.dto.MessagePageResponse;
import com.springchat.springchat_chat.modules.chat.dto.MessageResponse;
import com.springchat.springchat_chat.modules.chat.dto.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.entity.Message;
import com.springchat.springchat_chat.modules.chat.repository.ConversationParticipantRepository;
import com.springchat.springchat_chat.modules.chat.repository.ConversationRepository;
import com.springchat.springchat_chat.modules.chat.repository.MessageRepository;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SendResult sendMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        requireConversationMembership(conversationId, senderId);
        if (request.content().isBlank()) {
            throw new BadRequestException("Message content cannot be blank");
        }
        if (request.content().length() > 5000) {
            throw new BadRequestException("Message content must not exceed 5000 characters");
        }
        var reservation = idempotencyService.reserve(senderId, "chat.send-message.v1", request.idempotencyKey(),
                conversationId + "\n" + request.content());
        if (reservation.isReplay()) {
            Message existing = messageRepository.findByIdAndConversationId(reservation.resourceId(), conversationId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency result message missing"));
            if (!existing.getSender().getId().equals(senderId)) {
                throw new IllegalStateException("Idempotency result belongs to a different sender");
            }
            log.debug("Replaying message result conversationId={} messageId={}", conversationId, existing.getId());
            return new SendResult(toResponse(existing), false);
        }

        Message message = new Message();
        message.setConversation(conversationRepository.getReferenceById(conversationId));
        message.setSender(userRepository.getReferenceById(senderId));
        message.setContent(request.content());
        Message saved = messageRepository.saveAndFlush(message);
        idempotencyService.complete(reservation.id(), saved.getId());
        if (conversationRepository.updateLastActivityAt(conversationId, Instant.now()) != 1) {
            throw new IllegalStateException("Conversation activity could not be updated");
        }
        MessageResponse response = toResponse(saved);
        List<UUID> recipientIds = participantRepository.findMemberIds(conversationId);
        eventPublisher.publishEvent(new MessageCreatedEvent(response, Set.copyOf(recipientIds)));
        log.debug("Message insert prepared conversationId={} messageId={} senderId={}", conversationId,
                saved.getId(), senderId);
        return new SendResult(response, true);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse getMessageHistory(UUID conversationId, UUID userId, String cursor, int limit) {
        requireConversationMembership(conversationId, userId);
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }
        Cursor decoded = decodeCursor(cursor);
        List<Message> found = messageRepository.findHistory(conversationId,
                decoded == null ? null : decoded.createdAt(), decoded == null ? null : decoded.id(),
                PageRequest.of(0, limit + 1));
        boolean hasMore = found.size() > limit;
        List<Message> page = hasMore ? found.subList(0, limit) : found;
        String next = hasMore && !page.isEmpty() ? encodeCursor(page.getLast()) : null;
        log.debug("Message history loaded conversationId={} userId={} count={}", conversationId, userId, page.size());
        return new MessagePageResponse(page.stream().map(this::toResponse).toList(), next);
    }

    @Transactional
    public void markConversationRead(UUID conversationId, UUID userId, MarkReadRequest request) {
        requireConversationMembership(conversationId, userId);
        if (messageRepository.findByIdAndConversationId(request.messageId(), conversationId).isEmpty()) {
            throw new BadRequestException("messageId must identify a message in this conversation");
        }
        int advanced = participantRepository.advanceReadMarker(conversationId, userId, request.messageId());
        log.debug("Read marker update prepared conversationId={} userId={} advanced={}", conversationId,
                userId, advanced > 0);
    }

    private void requireConversationMembership(UUID conversationId, UUID userId) {
        if (!conversationRepository.isParticipant(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation not found");
        }
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(message.getId(), message.getConversation().getId(),
                message.getSender().getId(), message.getContent(), message.getCreatedAt());
    }

    private String encodeCursor(Message message) {
        String value = message.getCreatedAt() + "|" + message.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid message cursor");
        }
    }

    private record Cursor(Instant createdAt, UUID id) {}
    public record SendResult(MessageResponse message, boolean created) {}
}
