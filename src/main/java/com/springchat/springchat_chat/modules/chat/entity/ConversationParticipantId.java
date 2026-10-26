package com.springchat.springchat_chat.modules.chat.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable @Getter @Setter @NoArgsConstructor
public class ConversationParticipantId implements Serializable {
    @Column(name = "conversation_id") private UUID conversationId;
    @Column(name = "user_id") private UUID userId;
    public ConversationParticipantId(UUID conversationId, UUID userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ConversationParticipantId that)) return false;
        return Objects.equals(conversationId, that.conversationId) && Objects.equals(userId, that.userId);
    }
    @Override public int hashCode() { return Objects.hash(conversationId, userId); }
}
