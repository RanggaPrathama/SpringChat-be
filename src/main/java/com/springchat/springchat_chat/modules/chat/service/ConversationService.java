package com.springchat.springchat_chat.modules.chat.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.modules.chat.dto.ConversationResource;
import com.springchat.springchat_chat.modules.chat.dto.ConversationResponse;
import com.springchat.springchat_chat.modules.chat.dto.ConversationSummaryProjection;
import com.springchat.springchat_chat.modules.chat.dto.ConversationType;
import com.springchat.springchat_chat.modules.chat.dto.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.dto.MessageResponse;
import com.springchat.springchat_chat.modules.chat.entity.Conversation;
import com.springchat.springchat_chat.modules.chat.entity.ConversationParticipant;
import com.springchat.springchat_chat.modules.chat.entity.ConversationParticipantId;
import com.springchat.springchat_chat.modules.chat.repository.ConversationParticipantRepository;
import com.springchat.springchat_chat.modules.chat.repository.ConversationRepository;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreateResult createConversation(UUID currentUserId, CreateConversationRequest request) {
        if (request == null || request.type() == null) {
            throw new BadRequestException("Conversation type is required");
        }
        List<UUID> requestedIds = request.memberIds();
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.stream().anyMatch(id -> id == null)) {
            throw new BadRequestException("Conversation members are required");
        }
        if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new BadRequestException("Conversation member IDs must be distinct");
        }

        if (request.type() == ConversationType.DIRECT) {
            return createDirectConversation(currentUserId, request, requestedIds);
        }
        return createGroupConversation(currentUserId, request, requestedIds);
    }

    private CreateResult createDirectConversation(UUID currentUserId, CreateConversationRequest request, List<UUID> requestedIds) {
        if (requestedIds.size() != 1 || request.name() != null) {
            throw new BadRequestException("DIRECT requires one memberIds entry and no name");
        }
        UUID memberId = requestedIds.getFirst();
        if (currentUserId.equals(memberId)) {
            throw new BadRequestException("Self conversations are not allowed");
        }

        // Lock in UUID order to serialize reversed/concurrent DIRECT requests. NO KEY UPDATE
        // remains compatible with participant foreign-key key-share locks used by GROUP inserts.
        List<User> lockedUsers = userRepository.lockDirectConversationUsers(currentUserId, memberId);
        List<User> users = requireEnabledUsers(List.of(currentUserId, memberId), lockedUsers);
        var existingId = conversationRepository.findDirectConversationId(currentUserId, memberId);
        if (existingId.isPresent()) {
            log.debug("Reusing direct conversation conversationId={} requesterId={}", existingId.get(), currentUserId);
            return new CreateResult(getConversationById(currentUserId, existingId.get()), false);
        }

        Conversation conversation = saveConversation(ConversationType.DIRECT, null, users);
        log.debug("Direct conversation insert prepared conversationId={}", conversation.getId());
        return new CreateResult(getConversationById(currentUserId, conversation.getId()), true);
    }

    private CreateResult createGroupConversation(UUID currentUserId, CreateConversationRequest request, List<UUID> requestedIds) {
        String name = request.name();
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new BadRequestException("GROUP name must contain between 1 and 100 characters");
        }
        if (requestedIds.size() < 2) {
            throw new BadRequestException("A group requires at least two other members");
        }
        if (requestedIds.contains(currentUserId)) {
            throw new BadRequestException("Group member IDs must not include the creator");
        }

        Set<UUID> allMemberIds = new LinkedHashSet<>(requestedIds);
        allMemberIds.add(currentUserId);
        List<User> users = requireEnabledUsers(allMemberIds, userRepository.findAllById(allMemberIds));
        Conversation conversation = saveConversation(ConversationType.GROUP, name, users);
        log.debug("Group conversation insert prepared conversationId={} memberCount={}", conversation.getId(), users.size());
        return new CreateResult(getConversationById(currentUserId, conversation.getId()), true);
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
                throw new ResourceNotFoundException("User not found");
            }
            orderedUsers.add(user);
        }
        return orderedUsers;
    }

    private Conversation saveConversation(ConversationType type, String name, List<User> users) {
        Conversation conversation = new Conversation();
        conversation.setType(type.name());
        conversation.setName(name);
        Conversation savedConversation = conversationRepository.save(conversation);

        List<ConversationParticipant> participants = users.stream().map(user -> {
            ConversationParticipant participant = new ConversationParticipant();
            participant.setId(new ConversationParticipantId(savedConversation.getId(), user.getId()));
            participant.setConversation(savedConversation);
            participant.setUser(user);
            return participant;
        }).toList();
        participantRepository.saveAll(participants);
        return savedConversation;
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(UUID userId, Pageable pageable) {
        log.debug("Listing conversations userId={} page={}", userId, pageable.getPageNumber());
        return conversationRepository.findSummaries(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConversationResource getConversationById(UUID userId, UUID conversationId) {
        if (!conversationRepository.isParticipant(conversationId, userId)) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        var conversation = conversationRepository.findConversation(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        List<User> members = participantRepository.findMembers(conversationId);
        User other = "DIRECT".equals(conversation.getType())
                ? members.stream().filter(member -> !member.getId().equals(userId)).findFirst().orElse(null)
                : null;
        if ("DIRECT".equals(conversation.getType()) && (other == null || !other.isEnabled())) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        return new ConversationResource(conversationId, conversation.getType(),
                other == null ? null : userResponse(other), conversation.getCreatedAt(), conversation.getName(),
                members.stream().map(this::userResponse).toList());
    }

    private ConversationResponse toResponse(ConversationSummaryProjection row) {
        MessageResponse lastMessage = row.getLastMessageId() == null ? null
                : new MessageResponse(row.getLastMessageId(), row.getId(), row.getLastMessageSenderId(),
                        row.getLastMessageContent(), row.getLastMessageAt());
        return new ConversationResponse(row.getId(), row.getType(),
                row.getOtherUserId() == null ? null : new UserResponse(row.getOtherUserId(),
                        row.getOtherUsername(), row.getOtherEmail(), row.getOtherAvatarUrl()),
                lastMessage, row.getUnreadCount() == null ? 0 : row.getUnreadCount(), row.getLastActivityAt(),
                row.getName(), row.getMemberCount());
    }

    private UserResponse userResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatarUrl());
    }

    public record CreateResult(ConversationResource conversation, boolean created) {}
}
