package com.springchat.springchat_chat.modules.users.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springchat.springchat_chat.modules.users.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query(value = """
            SELECT * FROM users
            WHERE id IN (:currentUserId, :memberId)
            ORDER BY id
            FOR NO KEY UPDATE
            """, nativeQuery = true)
    List<User> lockDirectConversationUsers(@Param("currentUserId") UUID currentUserId,
            @Param("memberId") UUID memberId);



    @Query("""
            select u from User u
            where u.enabled = true and u.id <> :currentUserId
              and lower(u.username) like lower(concat('%', :query, '%')) escape '!'
            """)
    Page<User> searchDirectory(@Param("query") String query,
            @Param("currentUserId") UUID currentUserId,
            Pageable pageable);
}
