CREATE TABLE user_emotes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    emote_id VARCHAR(64) NOT NULL,
    purchased_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_emotes_user_emote UNIQUE (user_id, emote_id)
);

CREATE INDEX idx_user_emotes_user_id ON user_emotes (user_id);
