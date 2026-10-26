CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation VARCHAR(64) NOT NULL,
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_idempotency_keys_user_operation_key UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT ck_idempotency_keys_completion CHECK (
        (resource_id IS NULL AND completed_at IS NULL)
        OR (resource_id IS NOT NULL AND completed_at IS NOT NULL)
    )
);
