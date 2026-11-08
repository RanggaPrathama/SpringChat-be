package com.springchat.springchat_chat.modules.chat.service;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.common.idempotency.IdempotencyService;
import com.springchat.springchat_chat.common.response.CreationResult;
import com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.MarkReadRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationDetailResponse;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationSummaryResponse;
import com.springchat.springchat_chat.common.response.PaginatedResult;
import com.springchat.springchat_chat.common.response.PaginationMeta;
import com.springchat.springchat_chat.modules.chat.dto.response.MessageResponse;
import com.springchat.springchat_chat.modules.chat.entity.Conversation;
import com.springchat.springchat_chat.modules.chat.entity.Message;
import com.springchat.springchat_chat.modules.chat.enums.ConversationType;
import com.springchat.springchat_chat.modules.chat.event.MessageCreatedEvent;
import com.springchat.springchat_chat.modules.chat.mapper.ChatMapper;
import com.springchat.springchat_chat.modules.chat.repository.ConversationParticipantRepository;
import com.springchat.springchat_chat.modules.chat.repository.ConversationQueryRepository;
import com.springchat.springchat_chat.modules.chat.repository.ConversationRepository;
import com.springchat.springchat_chat.modules.chat.repository.MessageRepository;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.mapper.UserMapper;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private final ConversationRepository conversationRepository;
    private final ConversationQueryRepository conversationQueryRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CreationResult<ConversationDetailResponse> createConversation(UUID currentUserId, CreateConversationRequest request) {
        List<UUID> requestedIds = request.getMemberIds();
        if (request.getType() == ConversationType.DIRECT) {
            return createDirectConversation(currentUserId, request, requestedIds);
        }
        return createGroupConversation(currentUserId, request, requestedIds);
    }

    private CreationResult<ConversationDetailResponse> createDirectConversation(UUID currentUserId, CreateConversationRequest request, List<UUID> requestedIds) {
        UUID memberId = requestedIds.getFirst();
        if (currentUserId.equals(memberId)) {
            throw new BadRequestException("Pilih orang lain untuk memulai percakapan.");
        }

        // Lock in UUID order to serialize reversed/concurrent DIRECT requests. NO KEY UPDATE
        // remains compatible with participant foreign-key key-share locks used by GROUP inserts.
        List<User> lockedUsers = userRepository.lockDirectConversationUsers(currentUserId, memberId);
        List<User> users = requireEnabledUsers(List.of(currentUserId, memberId), lockedUsers);
        var existingId = conversationRepository.findDirectConversationId(currentUserId, memberId);
        if (existingId.isPresent()) {
            log.debug("Reusing direct conversation conversationId={} requesterId={}", existingId.get(), currentUserId);
            return new CreationResult<>(getConversationById(currentUserId, existingId.get()), false);
        }

        Conversation conversation = saveConversation(ConversationType.DIRECT, null, users);
        log.debug("Direct conversation insert prepared conversationId={}", conversation.getId());
        return new CreationResult<>(getConversationById(currentUserId, conversation.getId()), true);
    }

    private CreationResult<ConversationDetailResponse> createGroupConversation(UUID currentUserId, CreateConversationRequest request, List<UUID> requestedIds) {
        String name = request.getName();
        if (requestedIds.contains(currentUserId)) {
            throw new BadRequestException("Anda otomatis menjadi anggota grup. Pilih pengguna lain sebagai anggota.");
        }

        Set<UUID> allMemberIds = new LinkedHashSet<>(requestedIds);
        allMemberIds.add(currentUserId);
        List<User> users = requireEnabledUsers(allMemberIds, userRepository.findAllById(allMemberIds));
        Conversation conversation = saveConversation(ConversationType.GROUP, name, users);
        log.debug("Group conversation insert prepared conversationId={} memberCount={}", conversation.getId(), users.size());
        return new CreationResult<>(getConversationById(currentUserId, conversation.getId()), true);
    }

    private List<User> requireEnabledUsers(Iterable<UUID> requestedIds, List<User> foundUsers) {
        Map<UUID, User> usersById = new HashMap<>();
        for (User user : foundUsers) {
            usersById.put(user.getId(), user);
        }
        List<User> orderedUsers = new ArrayList<>();
        for (UUID requestedId : requestedIds) {
            User user = usersById.get(requestedId);
            if (user == null || !user.isEnabled()) {
                throw new ResourceNotFoundException("Pengguna tidak ditemukan atau sudah tidak tersedia.");
            }
            orderedUsers.add(user);
        }
        return orderedUsers;
    }

    private Conversation saveConversation(ConversationType type, String name, List<User> users) {
        Conversation conversation = ChatMapper.newConversation(type.name(), name);
        Conversation savedConversation = conversationRepository.save(conversation);
        var participants = users.stream().map(user ->
                ChatMapper.newParticipant(savedConversation, user)).toList();
        participantRepository.saveAll(participants);
        return savedConversation;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> listConversations(UUID userId, Pageable pageable) {
        log.debug("Listing conversations userId={} page={}", userId, pageable.getPageNumber());
        return conversationQueryRepository.findSummaries(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversationById(UUID userId, UUID conversationId) {
        if (!conversationRepository.isParticipant(conversationId, userId)) {
            throw new ResourceNotFoundException("Percakapan tidak ditemukan atau tidak dapat diakses.");
        }
        var conversation = conversationRepository.findConversation(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Percakapan tidak ditemukan atau tidak dapat diakses."));
        List<User> members = participantRepository.findMembers(conversationId);
        User other = "DIRECT".equals(conversation.getType())
                ? members.stream().filter(member -> !member.getId().equals(userId)).findFirst().orElse(null)
                : null;
        if ("DIRECT".equals(conversation.getType()) && (other == null || !other.isEnabled())) {
            throw new ResourceNotFoundException("Percakapan tidak ditemukan atau tidak dapat diakses.");
        }
        return ChatMapper.toDetailResponse(conversation, other, members);
    }

    @Override
    @Transactional
    public CreationResult<MessageResponse> sendMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        requireConversationMembership(conversationId, senderId);
        var reservation = idempotencyService.reserve(senderId, "chat.send-message.v1", request.getIdempotencyKey(),
                conversationId + "\n" + request.getContent());
        if (reservation.isReplay()) {
            Message existing = messageRepository.findByIdAndConversationId(reservation.resourceId(), conversationId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency result message missing"));
            if (!existing.getSender().getId().equals(senderId)) {
                throw new IllegalStateException("Idempotency result belongs to a different sender");
            }
            log.debug("Replaying message result conversationId={} messageId={}", conversationId, existing.getId());
            return new CreationResult<>(ChatMapper.toResponse(existing), false);
        }

        Message message = ChatMapper.toEntity(
                conversationRepository.getReferenceById(conversationId),
                userRepository.getReferenceById(senderId), request.getContent());
        Message saved = messageRepository.saveAndFlush(message);
        idempotencyService.complete(reservation.id(), saved.getId());
        if (conversationRepository.updateLastActivityAt(conversationId, Instant.now()) != 1) {
            throw new IllegalStateException("Conversation activity could not be updated");
        }
        MessageResponse response = ChatMapper.toResponse(saved);
        List<UUID> recipientIds = participantRepository.findMemberIds(conversationId);
        eventPublisher.publishEvent(new MessageCreatedEvent(response, Set.copyOf(recipientIds)));
        log.debug("Message insert prepared conversationId={} messageId={} senderId={}", conversationId,
                saved.getId(), senderId);
        return new CreationResult<>(response, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResult<MessageResponse> getMessageHistory(UUID conversationId, UUID userId, String cursor, int limit) {
        requireConversationMembership(conversationId, userId);
        Cursor decoded = decodeCursor(cursor);
        var pageable = PageRequest.of(0, limit + 1);
        List<Message> found = decoded == null
                ? messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, pageable)
                : messageRepository.findHistory(conversationId, decoded.createdAt(), decoded.id(), pageable);
        boolean hasMore = found.size() > limit;
        List<Message> page = hasMore ? found.subList(0, limit) : found;
        String next = hasMore && !page.isEmpty() ? encodeCursor(page.getLast()) : null;
        log.debug("Message history loaded conversationId={} userId={} count={}", conversationId, userId, page.size());
        return new PaginatedResult<>(page.stream().map(ChatMapper::toResponse).toList(),
                PaginationMeta.cursor(limit, hasMore, next));
    }

    @Override
    @Transactional
    public void markConversationRead(UUID conversationId, UUID userId, MarkReadRequest request) {
        requireConversationMembership(conversationId, userId);
        if (messageRepository.findByIdAndConversationId(request.getMessageId(), conversationId).isEmpty()) {
            throw new BadRequestException("Pesan tidak ditemukan dalam percakapan ini. Muat ulang percakapan Anda.");
        }
        int advanced = participantRepository.advanceReadMarker(conversationId, userId, request.getMessageId());
        log.debug("Read marker update prepared conversationId={} userId={} advanced={}", conversationId,
                userId, advanced > 0);
    }

    private void requireConversationMembership(UUID conversationId, UUID userId) {
        if (!conversationRepository.isParticipant(conversationId, userId)) {
            throw new ResourceNotFoundException("Percakapan tidak ditemukan atau tidak dapat diakses.");
        }
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
            throw new BadRequestException("Riwayat pesan belum dapat dimuat. Muat ulang percakapan Anda.");
        }
    }

    private record Cursor(Instant createdAt, UUID id) {}
}
