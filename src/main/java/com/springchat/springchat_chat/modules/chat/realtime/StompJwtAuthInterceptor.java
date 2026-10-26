package com.springchat.springchat_chat.modules.chat.realtime;

import java.util.List;
import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StompJwtAuthInterceptor implements ChannelInterceptor {
    private final JwtDecoder jwtDecoder;
    private final SessionExpiryEnforcer expiryEnforcer;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) return message;
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessagingException("Authentication required");
        }
        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(7));
            UUID userId = UUID.fromString(jwt.getSubject());
            accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), jwt, List.of()));
            if (accessor.getSessionId() != null && jwt.getExpiresAt() != null) {
                expiryEnforcer.schedule(accessor.getSessionId(), jwt.getExpiresAt());
            }
            return message;
        } catch (RuntimeException ex) {
            throw new MessagingException("Invalid access token");
        }
    }
}
