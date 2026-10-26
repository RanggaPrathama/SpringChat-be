package com.springchat.springchat_chat.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.DuplicateResourceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final JdbcTemplate jdbcTemplate;

    // Reservation, business writes, and completion must share the caller's transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation reserve(UUID userId, String operation, UUID key, String canonicalRequest) {
        if (key == null) {
            throw new BadRequestException("idempotencyKey is required");
        }
        String requestHash = hash(canonicalRequest);
        List<UUID> inserted = jdbcTemplate.query("""
                INSERT INTO idempotency_keys(user_id, operation, idempotency_key, request_hash)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, operation, idempotency_key) DO NOTHING
                RETURNING id
                """, (rs, row) -> rs.getObject("id", UUID.class), userId, operation, key, requestHash);
        if (!inserted.isEmpty()) {
            return new Reservation(inserted.getFirst(), null);
        }

        // A competing INSERT waits for its transaction to commit or roll back before returning here.
        StoredRequest stored = jdbcTemplate.queryForObject("""
                SELECT id, request_hash, resource_id FROM idempotency_keys
                WHERE user_id = ? AND operation = ? AND idempotency_key = ?
                """, (rs, row) -> new StoredRequest(rs.getObject("id", UUID.class),
                        rs.getString("request_hash"), rs.getObject("resource_id", UUID.class)),
                userId, operation, key);
        if (stored == null) {
            throw new IllegalStateException("Idempotency record missing after conflict");
        }
        if (!stored.requestHash().equals(requestHash)) {
            throw new DuplicateResourceException("idempotencyKey was already used with a different request");
        }
        if (stored.resourceId() == null) {
            throw new IllegalStateException("Idempotency record has no completed result");
        }
        return new Reservation(stored.id(), stored.resourceId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID reservationId, UUID resourceId) {
        int updated = jdbcTemplate.update("""
                UPDATE idempotency_keys SET resource_id = ?, completed_at = now()
                WHERE id = ? AND resource_id IS NULL
                """, resourceId, reservationId);
        if (updated != 1) {
            throw new IllegalStateException("Idempotency reservation cannot be completed");
        }
    }

    private String hash(String request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Reservation(UUID id, UUID resourceId) {
        public boolean isReplay() {
            return resourceId != null;
        }
    }

    private record StoredRequest(UUID id, String requestHash, UUID resourceId) {}
}
