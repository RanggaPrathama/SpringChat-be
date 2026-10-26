package com.springchat.springchat_chat.modules.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springchat.springchat_chat.modules.auth.entity.AuthSession;

import jakarta.persistence.LockModeType;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);
}
