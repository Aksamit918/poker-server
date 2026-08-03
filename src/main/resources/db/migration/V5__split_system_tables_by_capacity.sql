UPDATE game_tables SET max_players = 10
WHERE is_system = TRUE AND small_blind = 10 AND big_blind = 20 AND name = 'Последний додеп';

UPDATE game_tables SET max_players = 9
WHERE is_system = TRUE AND small_blind = 10 AND big_blind = 20 AND name = 'Отчаяние';

UPDATE game_tables SET max_players = 6
WHERE is_system = TRUE AND small_blind = 10 AND big_blind = 20 AND name = 'Безысходность';

INSERT INTO game_tables (
    id, name, small_blind, big_blind, min_players, max_players, is_private, is_system, created_at, min_buy_in, max_buy_in
) VALUES (
    gen_random_uuid(), 'На два фронта', 10, 20, 2, 2, FALSE, TRUE, NOW(), 200, 2000
);

UPDATE game_tables SET max_players = 10
WHERE is_system = TRUE AND small_blind = 100 AND big_blind = 200 AND name = 'Для гоев';

UPDATE game_tables SET max_players = 9
WHERE is_system = TRUE AND small_blind = 100 AND big_blind = 200 AND name = 'Not bad, not bad';

UPDATE game_tables SET max_players = 10
WHERE is_system = TRUE AND small_blind = 1000 AND big_blind = 2000 AND name = 'Лудоприключение';

UPDATE game_tables SET max_players = 10
WHERE is_system = TRUE AND small_blind = 100000 AND big_blind = 200000 AND name = 'Хамам';

UPDATE game_tables SET max_players = 9
WHERE is_system = TRUE AND small_blind = 100000 AND big_blind = 200000 AND name = 'Турецкая баня';

UPDATE game_tables SET max_players = 6
WHERE is_system = TRUE AND small_blind = 100000 AND big_blind = 200000 AND name = 'Римская баня';

UPDATE game_tables SET max_players = 2
WHERE is_system = TRUE AND small_blind = 100000 AND big_blind = 200000 AND name = 'Финская баня';

UPDATE game_tables SET max_players = 10
WHERE is_system = TRUE AND small_blind = 1000000 AND big_blind = 2000000 AND name = 'Лудоприключение VIP';
