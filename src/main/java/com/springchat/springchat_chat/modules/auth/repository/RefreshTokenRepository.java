package com.springchat.springchat_chat.modules.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springchat.springchat_chat.modules.auth.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    @Query("select t.session.id from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<UUID> findSessionIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE refresh_tokens SET consumed_at = :consumedAt "
            + "WHERE session_id = :sessionId AND consumed_at IS NULL", nativeQuery = true)
    int consumeUnconsumedBySessionId(@Param("sessionId") UUID sessionId,
            @Param("consumedAt") Instant consumedAt);
}
