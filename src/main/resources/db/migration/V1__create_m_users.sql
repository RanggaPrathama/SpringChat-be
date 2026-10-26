CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(50)  NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(2048),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_users_username_ci ON users (lower(username));
CREATE UNIQUE INDEX uk_users_email_ci ON users (lower(email));
