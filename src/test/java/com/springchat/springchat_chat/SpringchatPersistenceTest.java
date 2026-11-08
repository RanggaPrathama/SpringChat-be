package com.springchat.springchat_chat;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.DuplicateResourceException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.common.response.CreationResult;
import com.springchat.springchat_chat.modules.auth.dto.request.LoginRequest;
import com.springchat.springchat_chat.modules.auth.service.AuthService;
import com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.MarkReadRequest;
import com.springchat.springchat_chat.modules.chat.dto.request.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.dto.response.ConversationDetailResponse;
import com.springchat.springchat_chat.modules.chat.enums.ConversationType;
import com.springchat.springchat_chat.modules.chat.event.MessageCreatedEvent;
import com.springchat.springchat_chat.modules.chat.repository.ConversationRepository;
import com.springchat.springchat_chat.modules.chat.service.ChatService;
import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.response.UserResponse;
import com.springchat.springchat_chat.modules.users.service.UserService;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RecordApplicationEvents
class SpringchatPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserService userService;
    @Autowired AuthService authService;
    @Autowired ChatService chatService;
    @Autowired ConversationRepository conversationRepository;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired ApplicationEvents applicationEvents;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void flywayBuildsUuidSchemaAndUserCanRotateRefreshSession() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(migrations).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'messages' AND column_name IN ('client_message_id', 'idempotency_key')
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'users' AND column_name = 'id'
                """, String.class)).isEqualTo("uuid");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'users' AND column_name = 'display_name'
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'refresh_tokens' AND column_name = 'token_hash'
                """, String.class)).isEqualTo("character varying");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_name = 'refresh_tokens' AND column_name = 'token_hash'
                """, Integer.class)).isEqualTo(64);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'conversations' AND column_name IN ('pair_low', 'pair_high')
                """, Integer.class)).isZero();

        String username = uniqueName();
        var user = userService.registerUser(new CreateUserRequest(username, username + "@example.test", "password123"));
        var issued = authService.loginWithEmail(new LoginRequest(username + "@example.test", "password123")).orElseThrow();
        var decoded = jwtDecoder.decode(issued.access().getAccessToken());
        assertThat(decoded.getSubject()).isNotBlank();
        assertThat(decoded.getExpiresAt()).isAfter(java.time.Instant.now());
        String storedHash = jdbcTemplate.queryForObject("""
                SELECT t.token_hash FROM refresh_tokens t
                JOIN auth_sessions s ON s.id = t.session_id WHERE s.user_id = ?
                """, String.class, user.getId());
        assertThat(storedHash).hasSize(64);
        assertThat(storedHash).isNotEqualTo(issued.refreshToken());
        var rotated = authService.refreshSession(issued.refreshToken()).orElseThrow();
        assertThat(rotated.access().getAccessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(issued.refreshToken());

        assertThat(authService.refreshSession(issued.refreshToken())).isEmpty();
        assertThat(authService.refreshSession(rotated.refreshToken())).isEmpty();
    }

    @Test
    void concurrentRefreshHasAtMostOneSuccessAndReplayRevokesSession() throws Exception {
        String username = uniqueName();
        userService.registerUser(new CreateUserRequest(username, username + "@example.test", "password123"));
        var initial = authService.loginWithEmail(new LoginRequest(username + "@example.test", "password123")).orElseThrow();

        var first = CompletableFuture.supplyAsync(() -> authService.refreshSession(initial.refreshToken()));
        var second = CompletableFuture.supplyAsync(() -> authService.refreshSession(initial.refreshToken()));
        var resultA = first.get(20, TimeUnit.SECONDS);
        var resultB = second.get(20, TimeUnit.SECONDS);
        assertThat((resultA.isPresent() ? 1 : 0) + (resultB.isPresent() ? 1 : 0)).isEqualTo(1);
        var winner = resultA.orElseGet(resultB::orElseThrow);
        assertThat(authService.refreshSession(winner.refreshToken())).isEmpty();
    }

    @Test
    void logoutRevokesSessionAndConsumesCurrentTokenEvenWhenGivenAnOlderToken() {
        String username = uniqueName();
        userService.registerUser(new CreateUserRequest(username, username + "@example.test", "password123"));
        var initial = authService.loginWithEmail(new LoginRequest(username + "@example.test", "password123")).orElseThrow();
        var rotated = authService.refreshSession(initial.refreshToken()).orElseThrow();

        authService.logoutSession(initial.refreshToken());
        authService.logoutSession(rotated.refreshToken());

        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?",
                UUID.class, username + "@example.test");
        UUID sessionId = jdbcTemplate.queryForObject("SELECT id FROM auth_sessions WHERE user_id = ?",
                UUID.class, userId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_sessions WHERE id = ? AND revoked_at IS NOT NULL",
                Integer.class, sessionId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE session_id = ? AND consumed_at IS NULL",
                Integer.class, sessionId)).isZero();
        assertThat(authService.refreshSession(initial.refreshToken())).isEmpty();
        assertThat(authService.refreshSession(rotated.refreshToken())).isEmpty();
    }

    @Test
    void concurrentLogoutAndRefreshFinishWithRevokedSessionAndNoUnconsumedToken() throws Exception {
        String username = uniqueName();
        userService.registerUser(new CreateUserRequest(username, username + "@example.test", "password123"));
        var initial = authService.loginWithEmail(new LoginRequest(username + "@example.test", "password123")).orElseThrow();

        var refreshing = CompletableFuture.supplyAsync(() -> authService.refreshSession(initial.refreshToken()));
        var loggingOut = CompletableFuture.runAsync(() -> authService.logoutSession(initial.refreshToken()));
        var refreshed = refreshing.get(20, TimeUnit.SECONDS);
        loggingOut.get(20, TimeUnit.SECONDS);

        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?",
                UUID.class, username + "@example.test");
        UUID sessionId = jdbcTemplate.queryForObject("SELECT id FROM auth_sessions WHERE user_id = ?",
                UUID.class, userId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_sessions WHERE id = ? AND revoked_at IS NOT NULL",
                Integer.class, sessionId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE session_id = ? AND consumed_at IS NULL",
                Integer.class, sessionId)).isZero();
        refreshed.ifPresent(tokens -> assertThat(authService.refreshSession(tokens.refreshToken())).isEmpty());
    }

    @Test
    void loginUsesEmailOnlyNotUsername() {
        String username = uniqueName();
        userService.registerUser(new CreateUserRequest(username, username + "@example.test", "password123"));

        assertThat(authService.loginWithEmail(new LoginRequest(username + "@example.test", "password123"))).isPresent();
        assertThat(authService.loginWithEmail(new LoginRequest(username, "password123"))).isEmpty();
    }

    @Test
    void directConversationIsReusedAndMessageRetryAndReadMarkerAreConsistent() throws Exception {
        String aliceName = uniqueName();
        String bobName = uniqueName();
        String outsiderName = uniqueName();
        var alice = userService.registerUser(new CreateUserRequest(aliceName, aliceName + "@example.test", "password123"));
        var bob = userService.registerUser(new CreateUserRequest(bobName, bobName + "@example.test", "password123"));
        var outsider = userService.registerUser(new CreateUserRequest(outsiderName,
                outsiderName + "@example.test", "password123"));

        var first = CompletableFuture.supplyAsync(() -> createDirect(alice.getId(), bob.getId()));
        var second = CompletableFuture.supplyAsync(() -> createDirect(bob.getId(), alice.getId()));
        var firstResult = first.get(20, TimeUnit.SECONDS);
        var secondResult = second.get(20, TimeUnit.SECONDS);
        assertThat(firstResult.data().getId()).isEqualTo(secondResult.data().getId());
        assertThat((firstResult.created() ? 1 : 0) + (secondResult.created() ? 1 : 0)).isEqualTo(1);
        UUID conversationId = firstResult.data().getId();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversation_participants WHERE conversation_id = ?",
                Integer.class, conversationId)).isEqualTo(2);

        UUID idempotencyKey = UUID.randomUUID();
        var sent = chatService.sendMessage(conversationId, alice.getId(), new SendMessageRequest("hello", idempotencyKey));
        var retried = chatService.sendMessage(conversationId, alice.getId(), new SendMessageRequest("hello", idempotencyKey));
        assertThat(retried.data().getId()).isEqualTo(sent.data().getId());
        assertThatThrownBy(() -> chatService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("different", idempotencyKey)))
                .isInstanceOf(DuplicateResourceException.class);

        assertThatThrownBy(() -> chatService.getMessageHistory(conversationId, outsider.getId(), null, 30))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(chatService.listConversations(bob.getId(), PageRequest.of(0, 20)).getContent())
                .filteredOn(row -> row.getId().equals(conversationId))
                .singleElement().satisfies(row -> assertThat(row.getUnreadCount()).isEqualTo(1L));
        chatService.markConversationRead(conversationId, bob.getId(), new MarkReadRequest(sent.data().getId()));
        assertThat(chatService.listConversations(bob.getId(), PageRequest.of(0, 20)).getContent())
                .filteredOn(row -> row.getId().equals(conversationId))
                .singleElement().satisfies(row -> assertThat(row.getUnreadCount()).isZero());

        UUID secondMessageId = UUID.randomUUID();
        var secondMessage = chatService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("next", secondMessageId));
        chatService.markConversationRead(conversationId, bob.getId(), new MarkReadRequest(secondMessage.data().getId()));
        chatService.markConversationRead(conversationId, bob.getId(), new MarkReadRequest(sent.data().getId()));
        UUID storedMarker = jdbcTemplate.queryForObject("""
                SELECT last_read_message_id FROM conversation_participants
                WHERE conversation_id = ? AND user_id = ?
                """, UUID.class, conversationId, bob.getId());
        assertThat(storedMarker).isEqualTo(secondMessage.data().getId());

        chatService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("page-two", UUID.randomUUID()));
        var thirdPageMessage = chatService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("page-three", UUID.randomUUID()));
        var firstPage = chatService.getMessageHistory(conversationId, alice.getId(), null, 1);
        var nextPage = chatService.getMessageHistory(conversationId, alice.getId(), firstPage.pagination().getNextCursor(), 1);
        assertThat(firstPage.data()).hasSize(1);
        assertThat(nextPage.data()).hasSize(1);
        assertThat(firstPage.data().getFirst().getId()).isEqualTo(thirdPageMessage.data().getId());
        assertThat(nextPage.data().getFirst().getId()).isNotEqualTo(firstPage.data().getFirst().getId());

        UUID otherConversationId = createDirect(alice.getId(), outsider.getId())
                .data().getId();
        assertThatThrownBy(() -> chatService.sendMessage(otherConversationId, alice.getId(),
                new SendMessageRequest("hello", idempotencyKey)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(chatService.getMessageHistory(otherConversationId, alice.getId(), null, 30).data()).isEmpty();
        var unrelatedMessage = chatService.sendMessage(otherConversationId, alice.getId(),
                new SendMessageRequest("other chat", UUID.randomUUID()));
        assertThatThrownBy(() -> chatService.markConversationRead(conversationId, bob.getId(),
                new MarkReadRequest(unrelatedMessage.data().getId())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void groupsListOncePublishToEveryMemberAndKeepIndependentReadMarkers() {
        var alice = registerUser();
        var bob = registerUser();
        var carol = registerUser();
        var outsider = registerUser();
        List<UUID> memberIds = List.of(bob.getId(), carol.getId());
        var group = createGroup(alice.getId(), "  Study group  ", memberIds);
        assertThat(group.getType()).isEqualTo("GROUP");
        assertThat(group.getName()).isEqualTo("Study group");
        assertThat(group.getOtherUser()).isNull();
        assertThat(group.getCreatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM conversation_participants
                WHERE conversation_id = ? AND joined_at IS NOT NULL
                """, Integer.class, group.getId())).isEqualTo(3);
        assertThat(group.getMembers()).extracting(UserResponse::getId)
                .containsExactlyInAnyOrder(alice.getId(), bob.getId(), carol.getId());

        UUID idempotencyKey = UUID.randomUUID();
        var sent = chatService.sendMessage(group.getId(), alice.getId(), new SendMessageRequest("hello group", idempotencyKey));
        var retried = chatService.sendMessage(group.getId(), alice.getId(), new SendMessageRequest("hello group", idempotencyKey));
        assertThat(retried.created()).isFalse();
        assertThat(retried.data().getId()).isEqualTo(sent.data().getId());
        assertThat(applicationEvents.stream(MessageCreatedEvent.class)
                .filter(event -> event.message().getId().equals(sent.data().getId())).toList())
                .singleElement().satisfies(event -> assertThat(event.recipientIds())
                        .containsExactlyInAnyOrder(alice.getId(), bob.getId(), carol.getId()));

        for (UUID memberId : memberIds) {
            var page = chatService.listConversations(memberId, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).singleElement().satisfies(row -> {
                assertThat(row.getId()).isEqualTo(group.getId());
                assertThat(row.getName()).isEqualTo("Study group");
                assertThat(row.getMemberCount()).isEqualTo(3);
                assertThat(row.getOtherUser()).isNull();
                assertThat(row.getUnreadCount()).isEqualTo(1);
            });
        }
        chatService.markConversationRead(group.getId(), bob.getId(), new MarkReadRequest(sent.data().getId()));
        assertThat(chatService.listConversations(bob.getId(), PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.getUnreadCount()).isZero());
        assertThat(chatService.listConversations(carol.getId(), PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.getUnreadCount()).isEqualTo(1));
        assertThat(chatService.listConversations(alice.getId(), PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.getUnreadCount()).isZero());
        assertThat(chatService.listConversations(outsider.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThatThrownBy(() -> chatService.getConversationById(outsider.getId(), group.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chatService.getMessageHistory(group.getId(), outsider.getId(), null, 20))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chatService.sendMessage(group.getId(), outsider.getId(),
                new SendMessageRequest("intrusion", UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chatService.markConversationRead(group.getId(), outsider.getId(),
                new MarkReadRequest(sent.data().getId())))
                .isInstanceOf(ResourceNotFoundException.class);

        var separateGroup = createGroup(alice.getId(), "Study group", memberIds);
        assertThat(separateGroup.getId()).isNotEqualTo(group.getId());
        var direct = createDirect(alice.getId(), bob.getId()).data();
        assertThat(direct.getType()).isEqualTo("DIRECT");
        assertThat(direct.getName()).isNull();
        assertThat(direct.getOtherUser().getId()).isEqualTo(bob.getId());
        assertThat(direct.getMembers()).hasSize(2);
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", bob.getId());
        var visible = chatService.listConversations(alice.getId(), PageRequest.of(0, 1));
        assertThat(visible.getTotalElements()).isEqualTo(2);
        assertThat(visible.getContent()).singleElement().satisfies(row -> assertThat(row.getType()).isEqualTo("GROUP"));
    }

    @Test
    void conversationCreationRejectsInvalidTypeSpecificPayloads() {
        var creator = registerUser();
        var bob = registerUser();
        var carol = registerUser();
        var members = List.of(bob.getId(), carol.getId());
        assertThatThrownBy(() -> chatService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of())))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> chatService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, null, members)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> chatService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of(creator.getId()))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> chatService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, "name", List.of(bob.getId()))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), " ", members))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "x".repeat(101), members))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(bob.getId())))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(bob.getId(), bob.getId())))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(creator.getId(), bob.getId())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(bob.getId(), UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", carol.getId());
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", members))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(chatService.listConversations(creator.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void concurrentMessageRetriesCreateOneCompletedIdempotencyRecord() throws Exception {
        var alice = registerUser();
        var bob = registerUser();
        UUID conversationId = createDirect(alice.getId(), bob.getId()).data().getId();
        UUID key = UUID.randomUUID();
        var request = new SendMessageRequest("concurrent send", key);
        var first = CompletableFuture.supplyAsync(() -> chatService.sendMessage(conversationId, alice.getId(), request));
        var second = CompletableFuture.supplyAsync(() -> chatService.sendMessage(conversationId, alice.getId(), request));
        var firstResult = first.get(20, TimeUnit.SECONDS);
        var secondResult = second.get(20, TimeUnit.SECONDS);

        assertThat(firstResult.data().getId()).isEqualTo(secondResult.data().getId());
        assertThat((firstResult.created() ? 1 : 0) + (secondResult.created() ? 1 : 0)).isEqualTo(1);
        assertThat(chatService.getMessageHistory(conversationId, alice.getId(), null, 30).data()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM idempotency_keys
                WHERE user_id = ? AND operation = 'chat.send-message.v1' AND idempotency_key = ?
                  AND resource_id = ? AND completed_at IS NOT NULL
                """, Integer.class, alice.getId(), key, firstResult.data().getId())).isEqualTo(1);
    }

    @Test
    void differentSendersCanUseTheSameIdempotencyKey() {
        var alice = registerUser();
        var bob = registerUser();
        UUID conversationId = createDirect(alice.getId(), bob.getId()).data().getId();
        UUID key = UUID.randomUUID();
        var aliceMessage = chatService.sendMessage(conversationId, alice.getId(), new SendMessageRequest("hello", key));
        var bobMessage = chatService.sendMessage(conversationId, bob.getId(), new SendMessageRequest("hello", key));
        assertThat(aliceMessage.data().getId()).isNotEqualTo(bobMessage.data().getId());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM idempotency_keys WHERE idempotency_key = ?
                  AND user_id IN (?, ?) AND completed_at IS NOT NULL
                """, Integer.class, key, alice.getId(), bob.getId())).isEqualTo(2);
    }

    @Test
    void rolledBackSendLeavesNoMessageOrKeyAndCanBeRetried() {
        var alice = registerUser();
        var bob = registerUser();
        UUID conversationId = createDirect(alice.getId(), bob.getId()).data().getId();
        UUID key = UUID.randomUUID();
        var request = new SendMessageRequest("retry after failure", key);
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            chatService.sendMessage(conversationId, alice.getId(), request);
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM messages WHERE conversation_id = ?",
                Integer.class, conversationId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM idempotency_keys WHERE user_id = ? AND idempotency_key = ?
                """, Integer.class, alice.getId(), key)).isZero();
        assertThat(chatService.sendMessage(conversationId, alice.getId(), request).created()).isTrue();
    }

    @Test
    void conversationAndParticipantsRollBackTogether() {
        var alice = registerUser();
        var bob = registerUser();
        var carol = registerUser();
        String name = "Rollback " + UUID.randomUUID();
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            chatService.createConversation(alice.getId(), new CreateConversationRequest(
                    ConversationType.GROUP, name, List.of(bob.getId(), carol.getId())));
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM conversations WHERE name = ?",
                Integer.class, name)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM conversation_participants p
                JOIN conversations c ON c.id = p.conversation_id
                WHERE c.name = ?
                """, Integer.class, name)).isZero();
    }

    @Test
    void emptyConversationSummariesKeepNestedNullsAndAccuratePagination() {
        var alice = registerUser();
        var bob = registerUser();
        var carol = registerUser();
        var outsider = registerUser();
        var direct = createDirect(alice.getId(), bob.getId()).data();
        var group = createGroup(alice.getId(), "Summary pagination", List.of(bob.getId(), carol.getId()));
        jdbcTemplate.update("UPDATE conversations SET last_activity_at = '2026-01-01T00:00:00Z' WHERE id = ?", direct.getId());
        jdbcTemplate.update("UPDATE conversations SET last_activity_at = '2026-01-02T00:00:00Z' WHERE id = ?", group.getId());

        var first = chatService.listConversations(alice.getId(), PageRequest.of(0, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(group.getId());
            assertThat(row.getOtherUser()).isNull();
            assertThat(row.getLastMessage()).isNull();
            assertThat(row.getMemberCount()).isEqualTo(3);
            assertThat(row.getUnreadCount()).isZero();
        });
        var second = chatService.listConversations(alice.getId(), PageRequest.of(1, 1));
        assertThat(second.getTotalElements()).isEqualTo(2);
        assertThat(second.getContent()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(direct.getId());
            assertThat(row.getOtherUser().getId()).isEqualTo(bob.getId());
            assertThat(row.getOtherUser().getEmail()).isEqualTo(bob.getEmail());
            assertThat(row.getLastMessage()).isNull();
            assertThat(row.getMemberCount()).isEqualTo(2);
            assertThat(row.getUnreadCount()).isZero();
        });
        var beyondLast = chatService.listConversations(alice.getId(), PageRequest.of(2, 1));
        assertThat(beyondLast.getContent()).isEmpty();
        assertThat(beyondLast.getTotalElements()).isEqualTo(2);
        assertThat(chatService.listConversations(outsider.getId(), PageRequest.of(0, 1)).getTotalElements()).isZero();

        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", bob.getId());
        var visible = chatService.listConversations(alice.getId(), PageRequest.of(0, 10));
        assertThat(visible.getTotalElements()).isEqualTo(1);
        assertThat(visible.getContent()).singleElement().satisfies(row -> assertThat(row.getId()).isEqualTo(group.getId()));
    }

    @Test
    void summaryQuerySeesPendingJpaChangesWithoutCommittingThem() {
        var alice = registerUser();
        var bob = registerUser();
        var carol = registerUser();
        var group = createGroup(alice.getId(), "Original name", List.of(bob.getId(), carol.getId()));
        var transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            conversationRepository.findById(group.getId()).orElseThrow().setName("Pending change");
            assertThat(chatService.listConversations(alice.getId(), PageRequest.of(0, 20)).getContent())
                    .singleElement().satisfies(row -> assertThat(row.getName()).isEqualTo("Pending change"));
            status.setRollbackOnly();
        });

        assertThat(chatService.listConversations(alice.getId(), PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.getName()).isEqualTo("Original name"));
    }

    private CreationResult<ConversationDetailResponse> createDirect(UUID currentUserId, UUID memberId) {
        return chatService.createConversation(currentUserId,
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of(memberId)));
    }

    private ConversationDetailResponse createGroup(UUID creatorId, String name, List<UUID> memberIds) {
        return chatService.createConversation(creatorId,
                new CreateConversationRequest(ConversationType.GROUP, name, memberIds)).data();
    }

    private UserResponse registerUser() {
        String username = uniqueName();
        return userService.registerUser(new CreateUserRequest(username, username + "@example.test", "password123"));
    }

    private String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
