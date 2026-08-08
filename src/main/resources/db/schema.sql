CREATE TABLE IF NOT EXISTS users (
    username       VARCHAR(255) PRIMARY KEY,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS chats (
    chat_id     VARCHAR(26) PRIMARY KEY,
    username    VARCHAR(255) NOT NULL,
    chat_title  VARCHAR(255) NOT NULL,
    messages    JSONB NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_chats_username FOREIGN KEY (username) REFERENCES users (username) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chats_username_created_at ON chats (username, created_at);

CREATE TABLE IF NOT EXISTS anonymous_chats (
    chat_id     VARCHAR(26) PRIMARY KEY,
    session_id  VARCHAR(26) NOT NULL,
    chat_title  VARCHAR(255) NOT NULL DEFAULT 'New chat',
    messages    JSONB NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_anonymous_chats_session_id_created_at ON anonymous_chats (session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_anonymous_chats_updated_at ON anonymous_chats (updated_at);
