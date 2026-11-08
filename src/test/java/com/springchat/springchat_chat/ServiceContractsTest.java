package com.springchat.springchat_chat;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.ResourceNotFoundException;
import com.springchat.springchat_chat.common.exception.TooManyRequestsException;
import com.springchat.springchat_chat.common.idempotency.IdempotencyService;
import com.springchat.springchat_chat.modules.auth.repository.AuthSessionRepository;
import com.springchat.springchat_chat.modules.auth.repository.RefreshTokenRepository;
import com.springchat.springchat_chat.modules.auth.security.AuthRateLimiter;
import com.springchat.springchat_chat.modules.auth.security.JwtTokenService;
import com.springchat.springchat_chat.modules.auth.service.AuthService;
import com.springchat.springchat_chat.modules.auth.service.AuthServiceImpl;
import com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.enums.ConversationType;
import com.springchat.springchat_chat.modules.chat.repository.ConversationParticipantRepository;
import com.springchat.springchat_chat.modules.chat.repository.ConversationQueryRepository;
import com.springchat.springchat_chat.modules.chat.repository.ConversationRepository;
import com.springchat.springchat_chat.modules.chat.repository.MessageRepository;
import com.springchat.springchat_chat.modules.chat.service.ChatService;
import com.springchat.springchat_chat.modules.chat.service.ChatServiceImpl;
import com.springchat.springchat_chat.modules.users.dto.request.CreateUserRequest;
import com.springchat.springchat_chat.modules.users.dto.request.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.repository.UserRepository;
import com.springchat.springchat_chat.modules.users.service.UserService;
import com.springchat.springchat_chat.modules.users.service.UserServiceImpl;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig({AuthServiceImpl.class, UserServiceImpl.class, ChatServiceImpl.class,
        ServiceContractsTest.Transactions.class})
class ServiceContractsTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @org.springframework.context.annotation.Bean
        static org.springframework.validation.beanvalidation.MethodValidationPostProcessor methodValidation() {
            return new org.springframework.validation.beanvalidation.MethodValidationPostProcessor();
        }
    }

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private AuthSessionRepository sessionRepository;
    @MockitoBean private RefreshTokenRepository refreshTokenRepository;
    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private AuthRateLimiter rateLimiter;
    @MockitoBean private ConversationRepository conversationRepository;
    @MockitoBean private ConversationQueryRepository conversationQueryRepository;
    @MockitoBean private ConversationParticipantRepository participantRepository;
    @MockitoBean private MessageRepository messageRepository;
    @MockitoBean private IdempotencyService idempotencyService;
    @MockitoBean private PlatformTransactionManager transactionManager;

    @Autowired private ApplicationContext context;
    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private ChatService chatService;

    @Test
    void invalidRequestsAreRejectedThroughServiceProxyBeforeRepositoryAccess() {
        assertThatThrownBy(() -> chatService.createConversation(UUID.randomUUID(),
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of())))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> userService.updateOwnProfile(UUID.randomUUID(), new ProfileUpdateRequest(null, null)))
                .isInstanceOf(ConstraintViolationException.class);
        org.mockito.Mockito.verifyNoInteractions(conversationRepository, userRepository);
    }

    @Test
    void eachContractResolvesToOneTransactionalService() {
        assertThat(context.getBeansOfType(AuthService.class)).hasSize(1);
        assertThat(context.getBeansOfType(UserService.class)).hasSize(1);
        assertThat(context.getBeansOfType(ChatService.class)).hasSize(1);
        assertThat(AopUtils.isAopProxy(authService)).isTrue();
        assertThat(AopUtils.isAopProxy(userService)).isTrue();
        assertThat(AopUtils.isAopProxy(chatService)).isTrue();
    }

    @Test
    void businessFailuresRollBackWhenCalledThroughInterfaces() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> authService.registerUser(
                new CreateUserRequest("alice", "alice@example.test", "password123"), "127.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> userService.updateOwnProfile(userId, new ProfileUpdateRequest("alice", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chatService.createConversation(userId,
                new CreateConversationRequest(ConversationType.DIRECT, null, List.of(userId))))
                .isInstanceOf(BadRequestException.class);

        verify(transactionManager, times(3)).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void readOnlyUseCaseCommitsWhenCalledThroughInterface() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        UUID userId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        when(userRepository.searchDirectory("al", userId, pageable)).thenReturn(Page.empty());

        assertThat(userService.searchUsers("al", userId, pageable)).isEmpty();

        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
    }
}
