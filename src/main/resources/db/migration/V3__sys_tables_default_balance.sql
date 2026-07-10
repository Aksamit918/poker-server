ALTER TABLE accounts
    ALTER COLUMN wallet_balance SET DEFAULT 0;

ALTER TABLE game_tables ADD COLUMN min_buy_in BIGINT NOT NULL DEFAULT 0;
ALTER TABLE game_tables ADD COLUMN max_buy_in BIGINT NOT NULL DEFAULT 0;

ALTER TABLE game_tables
    ALTER COLUMN passcode TYPE VARCHAR(255);

INSERT INTO game_tables (
    id, name, small_blind, big_blind, min_players, max_players, is_private, is_system, created_at, min_buy_in, max_buy_in
) VALUES
      -- 10/20 Tables (Min: 200, Max: 2,000)
      (gen_random_uuid(), 'Последний додеп', 10, 20, 2, 9, FALSE, TRUE, NOW(), 200, 2000),
      (gen_random_uuid(), 'Отчаяние', 10, 20, 2, 9, FALSE, TRUE, NOW(), 200, 2000),
      (gen_random_uuid(), 'Безысходность', 10, 20, 2, 9, FALSE, TRUE, NOW(), 200, 2000),

      (gen_random_uuid(), 'Для гоев', 100, 200, 2, 9, FALSE, TRUE, NOW(), 2000, 20000),
      (gen_random_uuid(), 'Not bad, not bad', 100, 200, 2, 9, FALSE, TRUE, NOW(), 2000, 20000),

      (gen_random_uuid(), 'Лудоприключение', 1000, 2000, 2, 9, FALSE, TRUE, NOW(), 20000, 200000),

      (gen_random_uuid(), 'Хамам', 100000, 200000, 2, 9, FALSE, TRUE, NOW(), 2000000, 20000000),
      (gen_random_uuid(), 'Турецкая баня', 100000, 200000, 2, 9, FALSE, TRUE, NOW(), 2000000, 20000000),
      (gen_random_uuid(), 'Римская баня', 100000, 200000, 2, 9, FALSE, TRUE, NOW(), 2000000, 20000000),
      (gen_random_uuid(), 'Финская баня', 100000, 200000, 2, 9, FALSE, TRUE, NOW(), 2000000, 20000000),

      (gen_random_uuid(), 'Лудоприключение VIP', 1000000, 2000000, 2, 9, FALSE, TRUE, NOW(), 20000000, 200000000);