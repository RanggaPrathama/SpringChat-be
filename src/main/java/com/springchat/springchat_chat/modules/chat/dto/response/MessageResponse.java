package com.springchat.springchat_chat.modules.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MessageResponse {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("conversationId")
    private UUID conversationId;

    @JsonProperty("senderId")
    private UUID senderId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("createdAt")
    private Instant createdAt;

    public MessageResponse(UUID id, UUID conversationId, UUID senderId, String content, Instant createdAt) {
        setId(id);
        setConversationId(conversationId);
        setSenderId(senderId);
        setContent(content);
        setCreatedAt(createdAt);
    }

}
