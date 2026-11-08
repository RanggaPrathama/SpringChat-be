package com.springchat.springchat_chat.modules.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import com.springchat.springchat_chat.modules.chat.entity.Conversation;
import com.springchat.springchat_chat.modules.chat.entity.Message;
import com.springchat.springchat_chat.modules.chat.repository.ConversationRepository;
import com.springchat.springchat_chat.modules.chat.repository.MessageRepository;
import com.springchat.springchat_chat.modules.users.entity.User;

@ExtendWith(MockitoExtension.class)
class ChatHistoryPaginationTest {
    @Mock private ConversationRepository conversations;
    @Mock private MessageRepository messages;
    @InjectMocks private ChatServiceImpl service;

    @Test
    void cursorAdvancesWithoutOffsetAndDisappearsOnLastBatch() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var newest = message(conversationId, userId, Instant.parse("2026-01-02T00:00:00Z"));
        var older = message(conversationId, userId, Instant.parse("2026-01-01T00:00:00Z"));
        when(conversations.isParticipant(conversationId, userId)).thenReturn(true);
        when(messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, PageRequest.of(0, 2)))
                .thenReturn(List.of(newest, older));
        when(messages.findHistory(conversationId, newest.getCreatedAt(), newest.getId(), PageRequest.of(0, 2)))
                .thenReturn(List.of(older));

        var first = service.getMessageHistory(conversationId, userId, null, 1);
        assertThat(first.data()).singleElement().satisfies(row -> assertThat(row.getId()).isEqualTo(newest.getId()));
        assertThat(first.pagination().isHasNext()).isTrue();
        assertThat(first.pagination().getPage()).isNull();
        assertThat(first.pagination().getNextCursor()).isNotBlank();

        var last = service.getMessageHistory(conversationId, userId, first.pagination().getNextCursor(), 1);
        assertThat(last.data()).singleElement().satisfies(row -> assertThat(row.getId()).isEqualTo(older.getId()));
        assertThat(last.pagination().isHasNext()).isFalse();
        assertThat(last.pagination().getNextCursor()).isNull();
    }

    private Message message(UUID conversationId, UUID senderId, Instant createdAt) {
        var conversation = new Conversation();
        conversation.setId(conversationId);
        var sender = new User();
        sender.setId(senderId);
        var message = new Message();
        message.setId(UUID.randomUUID());
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent("Hello");
        message.setCreatedAt(createdAt);
        return message;
    }
}
