package com.springchat.springchat_chat.modules.chat.repository;

import com.springchat.springchat_chat.modules.chat.dto.response.ConversationSummaryResponse;
import com.springchat.springchat_chat.modules.chat.dto.response.MessageResponse;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@RequiredArgsConstructor
public class ConversationQueryRepository {
    private static final String SUMMARIES_SQL = """
            SELECT c.id, c.type, c.name,
                   (SELECT count(*) FROM conversation_participants p WHERE p.conversation_id = c.id) AS member_count,
                   other.id AS other_user_id, other.username AS other_username, other.email AS other_email,
                   other.avatar_url AS other_avatar_url,
                   last_msg.id AS last_message_id, last_msg.sender_id AS last_message_sender_id,
                   last_msg.content AS last_message_content, last_msg.created_at AS last_message_at,
                   c.last_activity_at, COUNT(unread.id) AS unread_count
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
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM conversations c
            JOIN conversation_participants mine ON mine.conversation_id = c.id AND mine.user_id = :userId
            WHERE c.type = 'GROUP' OR EXISTS (
                SELECT 1 FROM conversation_participants p JOIN users u ON u.id = p.user_id
                WHERE p.conversation_id = c.id AND p.user_id <> :userId AND u.enabled = TRUE
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public Page<ConversationSummaryResponse> findSummaries(UUID userId, Pageable pageable) {
        // JDBC cannot flush pending JPA writes as a native EntityManager query would.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                && entityManager.isJoinedToTransaction()) {
            entityManager.flush();
        }

        var parameters = new MapSqlParameterSource("userId", userId);
        String query = SUMMARIES_SQL;
        if (pageable.isPaged()) {
            query += " LIMIT :limit OFFSET :offset";
            parameters.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        }
        var conversations = jdbcTemplate.query(query, parameters, this::mapConversation);
        long total = Objects.requireNonNull(jdbcTemplate.queryForObject(COUNT_SQL, parameters, Long.class));
        return new PageImpl<>(conversations, pageable, total);
    }

    private ConversationSummaryResponse mapConversation(ResultSet row, int rowNumber) throws SQLException {
        UUID conversationId = row.getObject("id", UUID.class);
        UUID otherUserId = row.getObject("other_user_id", UUID.class);
        UserResponse otherUser = otherUserId == null ? null : new UserResponse(otherUserId,
                row.getString("other_username"), row.getString("other_email"), row.getString("other_avatar_url"));
        UUID messageId = row.getObject("last_message_id", UUID.class);
        MessageResponse lastMessage = messageId == null ? null : new MessageResponse(messageId, conversationId,
                row.getObject("last_message_sender_id", UUID.class), row.getString("last_message_content"),
                row.getTimestamp("last_message_at").toInstant());
        return new ConversationSummaryResponse(conversationId, row.getString("type"), otherUser, lastMessage,
                row.getLong("unread_count"), row.getTimestamp("last_activity_at").toInstant(),
                row.getString("name"), row.getLong("member_count"));
    }
}
