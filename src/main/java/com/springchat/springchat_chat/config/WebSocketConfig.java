package com.springchat.springchat_chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import com.springchat.springchat_chat.modules.chat.realtime.SessionExpiryEnforcer;
import com.springchat.springchat_chat.modules.chat.realtime.StompJwtAuthInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocketSecurity
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompJwtAuthInterceptor jwtAuthInterceptor;
    private final SessionExpiryEnforcer expiryEnforcer;

    @Value("${springchat.security.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigin);
    }

    @Override
    @Order(Ordered.HIGHEST_PRECEDENCE + 99)
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtAuthInterceptor);
    }

    @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                expiryEnforcer.track(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session,
                    org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
                expiryEnforcer.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages.nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers("/user/queue/messages").authenticated()
                .anyMessage().denyAll();
        return messages.build();
    }
}
