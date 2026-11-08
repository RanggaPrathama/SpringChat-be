package com.springchat.springchat_chat.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AccessTokenResponse {
    @JsonProperty("accessToken")
    private String accessToken;

    @JsonProperty("tokenType")
    private String tokenType;

    @JsonProperty("expiresIn")
    private long expiresIn;

    @JsonProperty("user")
    private UserResponse user;

    public AccessTokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
        setAccessToken(accessToken);
        setTokenType(tokenType);
        setExpiresIn(expiresIn);
        setUser(user);
    }

}
