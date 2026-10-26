package com.springchat.springchat_chat.modules.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springchat.springchat_chat.modules.chat.entity.ConversationParticipant;
import com.springchat.springchat_chat.modules.chat.entity.ConversationParticipantId;
import com.springchat.springchat_chat.modules.users.entity.User;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, ConversationParticipantId> {
    Optional<ConversationParticipant> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    @Query("select p.user from ConversationParticipant p where p.id.conversationId = :conversationId order by p.id.userId")
    List<User> findMembers(@Param("conversationId") UUID conversationId);

    @Query("select p.id.userId from ConversationParticipant p where p.id.conversationId = :conversationId")
    List<UUID> findMemberIds(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query(value = """
            UPDATE conversation_participants participant
            SET last_read_message_id = :messageId
            FROM messages candidate
            WHERE participant.conversation_id = :conversationId
              AND participant.user_id = :userId
              AND candidate.id = :messageId
              AND candidate.conversation_id = :conversationId
              AND (participant.last_read_message_id IS NULL OR EXISTS (
                   SELECT 1 FROM messages current_marker
                   WHERE current_marker.id = participant.last_read_message_id
                     AND (candidate.created_at > current_marker.created_at
                          OR (candidate.created_at = current_marker.created_at
                              AND candidate.id > current_marker.id))))
            """, nativeQuery = true)
    int advanceReadMarker(@Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId, @Param("messageId") UUID messageId);
}
