package com.springchat.springchat_chat.modules.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConversationSummaryResponse {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("otherUser")
    private UserResponse otherUser;

    @JsonProperty("lastMessage")
    private MessageResponse lastMessage;

    @JsonProperty("unreadCount")
    private long unreadCount;

    @JsonProperty("lastActivityAt")
    private Instant lastActivityAt;

    @JsonProperty("name")
    private String name;

    @JsonProperty("memberCount")
    private long memberCount;

    public ConversationSummaryResponse(UUID id, String type, UserResponse otherUser, MessageResponse lastMessage, long unreadCount, Instant lastActivityAt, String name, long memberCount) {
        setId(id);
        setType(type);
        setOtherUser(otherUser);
        setLastMessage(lastMessage);
        setUnreadCount(unreadCount);
        setLastActivityAt(lastActivityAt);
        setName(name);
        setMemberCount(memberCount);
    }

}
