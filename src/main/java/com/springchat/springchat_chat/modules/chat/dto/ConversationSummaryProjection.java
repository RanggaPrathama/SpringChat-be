package com.springchat.springchat_chat.modules.chat.dto;

import java.time.Instant;
import java.util.UUID;

public interface ConversationSummaryProjection {
    UUID getId();
    String getType();
    String getName();
    Long getMemberCount();
    UUID getOtherUserId();
    String getOtherUsername();
    String getOtherEmail();
    String getOtherAvatarUrl();
    UUID getLastMessageId();
    UUID getLastMessageSenderId();
    String getLastMessageContent();
    Instant getLastMessageAt();
    Instant getLastActivityAt();
    Long getUnreadCount();
}
