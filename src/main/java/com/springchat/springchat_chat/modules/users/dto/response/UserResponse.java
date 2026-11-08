package com.springchat.springchat_chat.modules.users.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserResponse {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("username")
    private String username;

    @JsonProperty("email")
    private String email;

    @JsonProperty("avatarUrl")
    private String avatarUrl;

    public UserResponse(UUID id, String username, String email, String avatarUrl) {
        setId(id);
        setUsername(username);
        setEmail(email);
        setAvatarUrl(avatarUrl);
    }

}
