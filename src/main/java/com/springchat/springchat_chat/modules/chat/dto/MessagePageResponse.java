package com.springchat.springchat_chat.modules.chat.dto;

import java.util.List;

public record MessagePageResponse(List<MessageResponse> items, String nextCursor) {
}
