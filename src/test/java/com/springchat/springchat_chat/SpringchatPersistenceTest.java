package com.springchat.springchat_chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.springchat.springchat_chat.common.exception.DuplicateResourceException;
import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.modules.auth.dto.LoginRequest;
import com.springchat.springchat_chat.modules.auth.service.AuthService;
import com.springchat.springchat_chat.modules.chat.dto.MarkReadRequest;
import com.springchat.springchat_chat.modules.chat.dto.MessageCreatedEvent;
import com.springchat.springchat_chat.modules.chat.dto.SendMessageRequest;
import com.springchat.springchat_chat.modules.chat.dto.ConversationResource;
import com.springchat.springchat_chat.modules.chat.dto.ConversationType;
import com.springchat.springchat_chat.modules.chat.dto.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.service.ConversationService;
import com.springchat.springchat_chat.modules.chat.service.MessageService;
import com.springchat.springchat_chat.modules.users.dto.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;
import com.springchat.springchat_chat.modules.users.entity.User;
import com.springchat.springchat_chat.modules.users.service.UserService;

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
    @Autowired ConversationService conversationService;
    @Autowired MessageService messageService;
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
        var decoded = jwtDecoder.decode(issued.access().accessToken());
        assertThat(decoded.getSubject()).isNotBlank();
        assertThat(decoded.getExpiresAt()).isAfter(java.time.Instant.now());
        String storedHash = jdbcTemplate.queryForObject("""
                SELECT t.token_hash FROM refresh_tokens t
                JOIN auth_sessions s ON s.id = t.session_id WHERE s.user_id = ?
                """, String.class, user.getId());
        assertThat(storedHash).hasSize(64);
        assertThat(storedHash).isNotEqualTo(issued.refreshToken());
        var rotated = authService.refreshSession(issued.refreshToken()).orElseThrow();
        assertThat(rotated.access().accessToken()).isNotBlank();
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
        assertThat(firstResult.conversation().id()).isEqualTo(secondResult.conversation().id());
        assertThat((firstResult.created() ? 1 : 0) + (secondResult.created() ? 1 : 0)).isEqualTo(1);
        UUID conversationId = firstResult.conversation().id();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversation_participants WHERE conversation_id = ?",
                Integer.class, conversationId)).isEqualTo(2);

        UUID idempotencyKey = UUID.randomUUID();
        var sent = messageService.sendMessage(conversationId, alice.getId(), new SendMessageRequest("hello", idempotencyKey));
        var retried = messageService.sendMessage(conversationId, alice.getId(), new SendMessageRequest("hello", idempotencyKey));
        assertThat(retried.message().id()).isEqualTo(sent.message().id());
        assertThatThrownBy(() -> messageService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("different", idempotencyKey)))
                .isInstanceOf(DuplicateResourceException.class);

        assertThatThrownBy(() -> messageService.getMessageHistory(conversationId, outsider.getId(), null, 30))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(conversationService.listConversations(bob.getId(), PageRequest.of(0, 20)).getContent())
                .filteredOn(row -> row.id().equals(conversationId))
                .singleElement().satisfies(row -> assertThat(row.unreadCount()).isEqualTo(1L));
        messageService.markConversationRead(conversationId, bob.getId(), new MarkReadRequest(sent.message().id()));
        assertThat(conversationService.listConversations(bob.getId(), PageRequest.of(0, 20)).getContent())
                .filteredOn(row -> row.id().equals(conversationId))
                .singleElement().satisfies(row -> assertThat(row.unreadCount()).isZero());

        UUID secondMessageId = UUID.randomUUID();
        var secondMessage = messageService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("next", secondMessageId));
        messageService.markConversationRead(conversationId, bob.getId(), new MarkReadRequest(secondMessage.message().id()));
        messageService.markConversationRead(conversationId, bob.getId(), new MarkReadRequest(sent.message().id()));
        UUID storedMarker = jdbcTemplate.queryForObject("""
                SELECT last_read_message_id FROM conversation_participants
                WHERE conversation_id = ? AND user_id = ?
                """, UUID.class, conversationId, bob.getId());
        assertThat(storedMarker).isEqualTo(secondMessage.message().id());

        messageService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("page-two", UUID.randomUUID()));
        var thirdPageMessage = messageService.sendMessage(conversationId, alice.getId(),
                new SendMessageRequest("page-three", UUID.randomUUID()));
        var firstPage = messageService.getMessageHistory(conversationId, alice.getId(), null, 1);
        var nextPage = messageService.getMessageHistory(conversationId, alice.getId(), firstPage.nextCursor(), 1);
        assertThat(firstPage.items()).hasSize(1);
        assertThat(nextPage.items()).hasSize(1);
        assertThat(firstPage.items().getFirst().id()).isEqualTo(thirdPageMessage.message().id());
        assertThat(nextPage.items().getFirst().id()).isNotEqualTo(firstPage.items().getFirst().id());

        UUID otherConversationId = createDirect(alice.getId(), outsider.getId())
                .conversation().id();
        assertThatThrownBy(() -> messageService.sendMessage(otherConversationId, alice.getId(),
                new SendMessageRequest("hello", idempotencyKey)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(messageService.getMessageHistory(otherConversationId, alice.getId(), null, 30).items()).isEmpty();
        var unrelatedMessage = messageService.sendMessage(otherConversationId, alice.getId(),
                new SendMessageRequest("other chat", UUID.randomUUID()));
        assertThatThrownBy(() -> messageService.markConversationRead(conversationId, bob.getId(),
                new MarkReadRequest(unrelatedMessage.message().id())))
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
        assertThat(group.type()).isEqualTo("GROUP");
        assertThat(group.name()).isEqualTo("Study group");
        assertThat(group.otherUser()).isNull();
        assertThat(group.createdAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM conversation_participants
                WHERE conversation_id = ? AND joined_at IS NOT NULL
                """, Integer.class, group.id())).isEqualTo(3);
        assertThat(group.members()).extracting(UserResponse::id)
                .containsExactlyInAnyOrder(alice.getId(), bob.getId(), carol.getId());

        UUID idempotencyKey = UUID.randomUUID();
        var sent = messageService.sendMessage(group.id(), alice.getId(), new SendMessageRequest("hello group", idempotencyKey));
        var retried = messageService.sendMessage(group.id(), alice.getId(), new SendMessageRequest("hello group", idempotencyKey));
        assertThat(retried.created()).isFalse();
        assertThat(retried.message().id()).isEqualTo(sent.message().id());
        assertThat(applicationEvents.stream(MessageCreatedEvent.class)
                .filter(event -> event.message().id().equals(sent.message().id())).toList())
                .singleElement().satisfies(event -> assertThat(event.recipientIds())
                        .containsExactlyInAnyOrder(alice.getId(), bob.getId(), carol.getId()));

        for (UUID memberId : memberIds) {
            var page = conversationService.listConversations(memberId, PageRequest.of(0, 20));
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).singleElement().satisfies(row -> {
                assertThat(row.id()).isEqualTo(group.id());
                assertThat(row.name()).isEqualTo("Study group");
                assertThat(row.memberCount()).isEqualTo(3);
                assertThat(row.otherUser()).isNull();
                assertThat(row.unreadCount()).isEqualTo(1);
            });
        }
        messageService.markConversationRead(group.id(), bob.getId(), new MarkReadRequest(sent.message().id()));
        assertThat(conversationService.listConversations(bob.getId(), PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.unreadCount()).isZero());
        assertThat(conversationService.listConversations(carol.getId(), PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.unreadCount()).isEqualTo(1));
        assertThat(conversationService.listConversations(alice.getId(), PageRequest.of(0, 20)).getContent())
                .singleElement().satisfies(row -> assertThat(row.unreadCount()).isZero());
        assertThat(conversationService.listConversations(outsider.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThatThrownBy(() -> conversationService.getConversationById(outsider.getId(), group.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> messageService.getMessageHistory(group.id(), outsider.getId(), null, 20))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> messageService.sendMessage(group.id(), outsider.getId(),
                new SendMessageRequest("intrusion", UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> messageService.markConversationRead(group.id(), outsider.getId(),
                new MarkReadRequest(sent.message().id())))
                .isInstanceOf(ResourceNotFoundException.class);

        var separateGroup = createGroup(alice.getId(), "Study group", memberIds);
        assertThat(separateGroup.id()).isNotEqualTo(group.id());
        var direct = createDirect(alice.getId(), bob.getId()).conversation();
        assertThat(direct.type()).isEqualTo("DIRECT");
        assertThat(direct.name()).isNull();
        assertThat(direct.otherUser().id()).isEqualTo(bob.getId());
        assertThat(direct.members()).hasSize(2);
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", bob.getId());
        var visible = conversationService.listConversations(alice.getId(), PageRequest.of(0, 1));
        assertThat(visible.getTotalElements()).isEqualTo(2);
        assertThat(visible.getContent()).singleElement().satisfies(row -> assertThat(row.type()).isEqualTo("GROUP"));
    }

    @Test
    void conversationCreationRejectsInvalidTypeSpecificPayloads() {
        var creator = registerUser();
        var bob = registerUser();
        var carol = registerUser();
        var members = List.of(bob.getId(), carol.getId());
        assertThatThrownBy(() -> conversationService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> conversationService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, null, members)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> conversationService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of(creator.getId()))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> conversationService.createConversation(creator.getId(),
                new CreateConversationRequest(ConversationType.DIRECT, "name", List.of(bob.getId()))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), " ", members))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "x".repeat(101), members))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(bob.getId())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(bob.getId(), bob.getId())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(creator.getId(), bob.getId())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", List.of(bob.getId(), UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", carol.getId());
        assertThatThrownBy(() -> createGroup(creator.getId(), "Group", members))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(conversationService.listConversations(creator.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void concurrentMessageRetriesCreateOneCompletedIdempotencyRecord() throws Exception {
        var alice = registerUser();
        var bob = registerUser();
        UUID conversationId = createDirect(alice.getId(), bob.getId()).conversation().id();
        UUID key = UUID.randomUUID();
        var request = new SendMessageRequest("concurrent send", key);
        var first = CompletableFuture.supplyAsync(() -> messageService.sendMessage(conversationId, alice.getId(), request));
        var second = CompletableFuture.supplyAsync(() -> messageService.sendMessage(conversationId, alice.getId(), request));
        var firstResult = first.get(20, TimeUnit.SECONDS);
        var secondResult = second.get(20, TimeUnit.SECONDS);

        assertThat(firstResult.message().id()).isEqualTo(secondResult.message().id());
        assertThat((firstResult.created() ? 1 : 0) + (secondResult.created() ? 1 : 0)).isEqualTo(1);
        assertThat(messageService.getMessageHistory(conversationId, alice.getId(), null, 30).items()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM idempotency_keys
                WHERE user_id = ? AND operation = 'chat.send-message.v1' AND idempotency_key = ?
                  AND resource_id = ? AND completed_at IS NOT NULL
                """, Integer.class, alice.getId(), key, firstResult.message().id())).isEqualTo(1);
    }

    @Test
    void differentSendersCanUseTheSameIdempotencyKey() {
        var alice = registerUser();
        var bob = registerUser();
        UUID conversationId = createDirect(alice.getId(), bob.getId()).conversation().id();
        UUID key = UUID.randomUUID();
        var aliceMessage = messageService.sendMessage(conversationId, alice.getId(), new SendMessageRequest("hello", key));
        var bobMessage = messageService.sendMessage(conversationId, bob.getId(), new SendMessageRequest("hello", key));
        assertThat(aliceMessage.message().id()).isNotEqualTo(bobMessage.message().id());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM idempotency_keys WHERE idempotency_key = ?
                  AND user_id IN (?, ?) AND completed_at IS NOT NULL
                """, Integer.class, key, alice.getId(), bob.getId())).isEqualTo(2);
    }

    @Test
    void rolledBackSendLeavesNoMessageOrKeyAndCanBeRetried() {
        var alice = registerUser();
        var bob = registerUser();
        UUID conversationId = createDirect(alice.getId(), bob.getId()).conversation().id();
        UUID key = UUID.randomUUID();
        var request = new SendMessageRequest("retry after failure", key);
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            messageService.sendMessage(conversationId, alice.getId(), request);
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM messages WHERE conversation_id = ?",
                Integer.class, conversationId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM idempotency_keys WHERE user_id = ? AND idempotency_key = ?
                """, Integer.class, alice.getId(), key)).isZero();
        assertThat(messageService.sendMessage(conversationId, alice.getId(), request).created()).isTrue();
    }

    @Test
    void conversationAndParticipantsRollBackTogether() {
        var alice = registerUser();
        var bob = registerUser();
        var carol = registerUser();
        String name = "Rollback " + UUID.randomUUID();
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            conversationService.createConversation(alice.getId(), new CreateConversationRequest(
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

    private ConversationService.CreateResult createDirect(UUID currentUserId, UUID memberId) {
        return conversationService.createConversation(currentUserId,
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of(memberId)));
    }

    private ConversationResource createGroup(UUID creatorId, String name, List<UUID> memberIds) {
        return conversationService.createConversation(creatorId,
                new CreateConversationRequest(ConversationType.GROUP, name, memberIds)).conversation();
    }

    private User registerUser() {
        String username = uniqueName();
        return userService.registerUser(new CreateUserRequest(username, username + "@example.test", "password123"));
    }

    private String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
