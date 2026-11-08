package com.springchat.springchat_chat.modules.auth.service;

import com.springchat.springchat_chat.modules.auth.dto.response.AccessTokenResponse;
import java.time.Duration;

/** Internal credentials delivered to the HTTP adapter; never serialize this as an API body. */
public record IssuedTokens(AccessTokenResponse access, String refreshToken, Duration refreshExpiresIn) {}
