package com.springchat.springchat_chat.modules.chat.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversations")
@Getter @Setter @NoArgsConstructor
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 16)
    private String type = "DIRECT";
    @Column(length = 100)
    private String name;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastActivityAt == null) {
            lastActivityAt = now;
        }
    }
}
