CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(16) NOT NULL DEFAULT 'DIRECT' CHECK (type IN ('DIRECT', 'GROUP')),
    name VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_conversations_name CHECK (
        (type = 'DIRECT' AND name IS NULL)
        OR (type = 'GROUP' AND name IS NOT NULL AND name ~ '[^[:space:]]')
    )
);
