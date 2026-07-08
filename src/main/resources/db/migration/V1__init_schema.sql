CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    google_id VARCHAR(255) UNIQUE,
    email VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(20) NOT NULL,
    wallet_balance BIGINT NOT NULL,
    last_bonus_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    hands_played INTEGER DEFAULT 0 NOT NULL,
    hands_won INTEGER DEFAULT 0 NOT NULL,
    total_won BIGINT DEFAULT 0 NOT NULL,
    biggest_pot BIGINT DEFAULT 0 NOT NULL
);

CREATE TABLE game_tables (
     id UUID PRIMARY KEY,
     name VARCHAR(20) NOT NULL,
     small_blind BIGINT NOT NULL,
     big_blind BIGINT NOT NULL,
     min_players INTEGER NOT NULL,
     max_players INTEGER NOT NULL,
     is_private BOOLEAN NOT NULL,
     passcode VARCHAR(50),
     is_system BOOLEAN NOT NULL,
     creator_id BIGINT REFERENCES accounts(id),
     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,

     CHECK (small_blind > 0 AND big_blind > 0 AND small_blind < big_blind),
     CHECK (min_players >= 2 AND max_players <= 10 AND min_players <= max_players)
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    table_id UUID REFERENCES game_tables(id),
    amount BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

