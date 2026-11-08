package com.springchat.springchat_chat.modules.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CsrfPayload {
    @JsonProperty("headerName")
    private String headerName;

    @JsonProperty("token")
    private String token;

    public CsrfPayload(String headerName, String token) {
        setHeaderName(headerName);
        setToken(token);
    }

}
