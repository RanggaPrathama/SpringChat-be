CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    content VARCHAR(5000) NOT NULL CHECK (length(content) BETWEEN 1 AND 5000 AND content ~ '[^[:space:]]'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_messages_conversation_id UNIQUE (conversation_id, id)
);

CREATE INDEX ix_messages_conversation_history ON messages(conversation_id, created_at DESC, id DESC);
