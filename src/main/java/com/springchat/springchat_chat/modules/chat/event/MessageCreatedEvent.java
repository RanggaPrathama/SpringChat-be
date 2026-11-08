package com.springchat.springchat_chat.modules.chat.event;

import com.springchat.springchat_chat.modules.chat.dto.response.MessageResponse;
import java.util.Set;
import java.util.UUID;

public record MessageCreatedEvent(MessageResponse message, Set<UUID> recipientIds) {
}
