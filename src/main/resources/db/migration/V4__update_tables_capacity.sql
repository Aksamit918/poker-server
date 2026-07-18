UPDATE game_tables
SET max_players = 10
WHERE max_players = 9 AND is_system = TRUE;