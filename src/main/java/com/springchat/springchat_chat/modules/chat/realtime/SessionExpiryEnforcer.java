package com.springchat.springchat_chat.modules.chat.realtime;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SessionExpiryEnforcer {
    private final TaskScheduler taskScheduler;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> expirations = new ConcurrentHashMap<>();

    public SessionExpiryEnforcer(@Qualifier("webSocketTaskScheduler") TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void track(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void schedule(String sessionId, Instant expiresAt) {
        ScheduledFuture<?> previous = expirations.remove(sessionId);
        if (previous != null) previous.cancel(false);
        expirations.put(sessionId, taskScheduler.schedule(() -> close(sessionId), Date.from(expiresAt)));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
        ScheduledFuture<?> task = expirations.remove(sessionId);
        if (task != null) task.cancel(false);
    }

    private void close(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Access token expired"));
            } catch (IOException ignored) {
                // The peer may already have closed the transport.
            }
        }
        expirations.remove(sessionId);
    }
}
