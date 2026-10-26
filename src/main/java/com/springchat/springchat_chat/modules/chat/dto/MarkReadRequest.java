package com.springchat.springchat_chat.modules.chat.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(@NotNull UUID messageId) {
}
