package com.springchat.springchat_chat.modules.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConversationDetailResponse {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("type")
    private String type;

    @JsonProperty("otherUser")
    private UserResponse otherUser;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("name")
    private String name;

    @JsonProperty("members")
    private List<UserResponse> members;

    public ConversationDetailResponse(UUID id, String type, UserResponse otherUser, Instant createdAt, String name, List<UserResponse> members) {
        setId(id);
        setType(type);
        setOtherUser(otherUser);
        setCreatedAt(createdAt);
        setName(name);
        setMembers(members);
    }

}
