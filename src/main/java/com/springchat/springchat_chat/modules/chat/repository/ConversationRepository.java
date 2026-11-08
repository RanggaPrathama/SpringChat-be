package com.springchat.springchat_chat.modules.chat.repository;

import com.springchat.springchat_chat.modules.chat.entity.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("select count(p) > 0 from ConversationParticipant p where p.id.conversationId = :conversationId and p.id.userId = :userId")
    boolean isParticipant(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("select c from Conversation c where c.id = :id")
    java.util.Optional<Conversation> findConversation(@Param("id") UUID id);
}
