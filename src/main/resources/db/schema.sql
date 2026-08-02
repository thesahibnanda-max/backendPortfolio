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
