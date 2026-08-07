DO $$
DECLARE
  constraint_name text;
BEGIN
  FOR constraint_name IN
    SELECT con.conname
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'game_tables'
      AND con.contype = 'c'
  LOOP
    EXECUTE format('ALTER TABLE game_tables DROP CONSTRAINT %I', constraint_name);
  END LOOP;
END $$;

UPDATE game_tables
SET max_players = CASE
  WHEN max_players <= 2 THEN 2
  WHEN max_players <= 4 THEN 4
  WHEN max_players <= 6 THEN 6
  WHEN max_players <= 9 THEN 9
  ELSE 10
END
WHERE max_players NOT IN (2, 4, 6, 9, 10);

UPDATE game_tables
SET min_players = 2
WHERE min_players < 2;

UPDATE game_tables
SET min_players = max_players
WHERE min_players > max_players;

ALTER TABLE game_tables
  ADD CONSTRAINT game_tables_blinds_check
  CHECK (small_blind > 0 AND big_blind > 0 AND small_blind < big_blind);

ALTER TABLE game_tables
  ADD CONSTRAINT game_tables_players_capacity_check
  CHECK (min_players >= 2 AND max_players IN (2, 4, 6, 9, 10) AND min_players <= max_players);
