CREATE TABLE conversation_participants (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_message_id UUID,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_participant_read_message
        FOREIGN KEY (conversation_id, last_read_message_id)
        REFERENCES messages(conversation_id, id)
);

CREATE INDEX ix_conversation_participants_user ON conversation_participants(user_id, conversation_id);
