package com.springchat.springchat_chat.modules.chat.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springchat.springchat_chat.modules.chat.dto.ConversationSummaryProjection;
import com.springchat.springchat_chat.modules.chat.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    @Modifying
    @Query("update Conversation c set c.lastActivityAt = :activityAt where c.id = :conversationId")
    int updateLastActivityAt(@Param("conversationId") UUID conversationId,
            @Param("activityAt") java.time.Instant activityAt);

    @Query(value = """
            SELECT c.id
            FROM conversations c
            JOIN conversation_participants mine
              ON mine.conversation_id = c.id AND mine.user_id = :currentUserId
            JOIN conversation_participants theirs
              ON theirs.conversation_id = c.id AND theirs.user_id = :memberId
            WHERE c.type = 'DIRECT'
              AND (SELECT count(*) FROM conversation_participants p WHERE p.conversation_id = c.id) = 2
            ORDER BY c.created_at, c.id
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findDirectConversationId(@Param("currentUserId") UUID currentUserId,
            @Param("memberId") UUID memberId);

    @Query(value = """
            SELECT c.id AS id, c.type AS type, c.name AS name,
                   (SELECT count(*) FROM conversation_participants p WHERE p.conversation_id = c.id) AS memberCount,
                   other.id AS otherUserId, other.username AS otherUsername, other.email AS "otherEmail",
                   other.avatar_url AS otherAvatarUrl,
                   last_msg.id AS lastMessageId, last_msg.sender_id AS lastMessageSenderId,
                   last_msg.content AS lastMessageContent,
                   last_msg.created_at AS lastMessageAt, c.last_activity_at AS lastActivityAt,
                   COUNT(unread.id) AS unreadCount
            FROM conversations c
            JOIN conversation_participants mine
              ON mine.conversation_id = c.id AND mine.user_id = :userId
            LEFT JOIN conversation_participants theirs
              ON theirs.conversation_id = c.id AND theirs.user_id <> :userId AND c.type = 'DIRECT'
            LEFT JOIN users other ON other.id = theirs.user_id
            LEFT JOIN messages marker ON marker.id = mine.last_read_message_id
            LEFT JOIN LATERAL (
                SELECT m.id, m.sender_id, m.content, m.created_at
                FROM messages m
                WHERE m.conversation_id = c.id
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 1
            ) last_msg ON TRUE
            LEFT JOIN messages unread
              ON unread.conversation_id = c.id
             AND unread.sender_id <> :userId
             AND (marker.id IS NULL OR unread.created_at > marker.created_at
                  OR (unread.created_at = marker.created_at AND unread.id > marker.id))
            WHERE c.type = 'GROUP' OR other.enabled = TRUE
            GROUP BY c.id, other.id, other.email, last_msg.id, last_msg.sender_id,
                     last_msg.content, last_msg.created_at
            ORDER BY c.last_activity_at DESC, c.id DESC
            """, countQuery = """
            SELECT COUNT(*)
            FROM conversations c
            JOIN conversation_participants mine ON mine.conversation_id = c.id AND mine.user_id = :userId
            WHERE c.type = 'GROUP' OR EXISTS (
                SELECT 1 FROM conversation_participants p JOIN users u ON u.id = p.user_id
                WHERE p.conversation_id = c.id AND p.user_id <> :userId AND u.enabled = TRUE
            )
            """, nativeQuery = true)
    Page<ConversationSummaryProjection> findSummaries(@Param("userId") UUID userId, Pageable pageable);

    @Query("select count(p) > 0 from ConversationParticipant p where p.id.conversationId = :conversationId and p.id.userId = :userId")
    boolean isParticipant(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("select c from Conversation c where c.id = :id")
    java.util.Optional<Conversation> findConversation(@Param("id") UUID id);
}
