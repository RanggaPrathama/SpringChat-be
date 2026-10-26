package com.springchat.springchat_chat.modules.chat.entity;

import java.time.Instant;
import java.util.UUID;
import com.springchat.springchat_chat.modules.users.entity.User;
import org.springframework.data.domain.Persistable;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "conversation_participants")
@Getter @Setter @NoArgsConstructor
public class ConversationParticipant implements Persistable<ConversationParticipantId> {
    @EmbeddedId private ConversationParticipantId id;
    @MapsId("conversationId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id") private Conversation conversation;
    @MapsId("userId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id") private User user;
    @Column(name = "joined_at", nullable = false) private Instant joinedAt;
    @Column(name = "last_read_message_id") private UUID lastReadMessageId;
    @Transient private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    @jakarta.persistence.PrePersist
    void onJoin() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }
}
