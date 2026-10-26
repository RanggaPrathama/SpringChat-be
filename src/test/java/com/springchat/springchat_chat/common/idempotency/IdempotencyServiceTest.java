package com.springchat.springchat_chat.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.springchat.springchat_chat.common.exception.BadRequestException;
import com.springchat.springchat_chat.common.exception.DuplicateResourceException;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {
    @Mock private JdbcTemplate jdbcTemplate;
    private IdempotencyService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID key = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private static final String OPERATION = "chat.send-message.v1";
    private static final String REQUEST = "00000000-0000-0000-0000-000000000001\nHello";

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(jdbcTemplate);
    }

    @Test
    void matchingRequestReplaysTheStoredResource() throws Exception {
        UUID messageId = UUID.randomUUID();
        existingReservation(messageId);
        var reservation = service.reserve(userId, OPERATION, key, REQUEST);
        assertThat(reservation.isReplay()).isTrue();
        assertThat(reservation.resourceId()).isEqualTo(messageId);
        assertThat(reservation.id()).isEqualTo(reservationId);
    }

    @Test
    void changedContentOrConversationIsRejected() throws Exception {
        existingReservation(UUID.randomUUID());
        for (String differentRequest : List.of(
                "00000000-0000-0000-0000-000000000002\nHello",
                "00000000-0000-0000-0000-000000000001\nChanged")) {
            assertThatThrownBy(() -> service.reserve(userId, OPERATION, key, differentRequest))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Test
    void incompleteCommittedRecordCannotBeTreatedAsAReplay() throws Exception {
        existingReservation(null);
        assertThatThrownBy(() -> service.reserve(userId, OPERATION, key, REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Idempotency record has no completed result");
    }

    @Test
    void missingKeyDoesNotTouchTheDatabase() {
        assertThatThrownBy(() -> service.reserve(userId, OPERATION, null, REQUEST))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    private void existingReservation(UUID resourceId) throws Exception {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<UUID>>any(),
                eq(userId), eq(OPERATION), eq(key), anyString())).thenReturn(List.of());
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(reservationId);
        when(resultSet.getObject("resource_id", UUID.class)).thenReturn(resourceId);
        when(resultSet.getString("request_hash")).thenReturn(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(REQUEST.getBytes(StandardCharsets.UTF_8))));
        when(jdbcTemplate.queryForObject(anyString(), ArgumentMatchers.<RowMapper<Object>>any(),
                eq(userId), eq(OPERATION), eq(key))).thenAnswer(invocation -> {
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    return rowMapper.mapRow(resultSet, 0);
                });
    }
}
