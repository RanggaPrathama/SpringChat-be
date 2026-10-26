package com.springchat.springchat_chat.modules.chat.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import com.springchat.springchat_chat.modules.chat.dto.MessageCreatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MessageEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(MessageCreatedEvent event) {
        event.recipientIds().forEach(userId ->
                messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/messages", event.message()));
    }
}
