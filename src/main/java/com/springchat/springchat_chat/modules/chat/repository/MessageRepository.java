package com.springchat.springchat_chat.modules.chat.repository;

import com.springchat.springchat_chat.modules.chat.entity.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    List<Message> findByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId, Pageable pageable);

    @Query("""
            select m from Message m
            where m.conversation.id = :conversationId
              and (m.createdAt < :cursorTime
                   or (m.createdAt = :cursorTime and m.id < :cursorId))
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findHistory(@Param("conversationId") UUID conversationId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
