package com.springchat.springchat_chat.modules.chat.dto;

import java.util.Set;
import java.util.UUID;

public record MessageCreatedEvent(MessageResponse message, Set<UUID> recipientIds) {
}
