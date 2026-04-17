CREATE TABLE bot_user (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    avatar_url  VARCHAR(500),
    bot_type    VARCHAR(50) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    config      JSONB
);

ALTER TABLE game_players
    ADD COLUMN bot_user_id UUID REFERENCES bot_user(id) ON DELETE CASCADE;

ALTER TABLE game_players
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE game_players
    ADD CONSTRAINT chk_game_players_user_or_bot
        CHECK (
            (user_id IS NOT NULL AND bot_user_id IS NULL)
                OR (user_id IS NULL AND bot_user_id IS NOT NULL)
            );

CREATE UNIQUE INDEX uq_game_players_game_bot
    ON game_players(game_id, bot_user_id)
    WHERE bot_user_id IS NOT NULL;

CREATE INDEX idx_game_players_bot_user ON game_players(bot_user_id);
CREATE INDEX idx_bot_user_type_active ON bot_user(bot_type, is_active);

INSERT INTO bot_user (name, bot_type, avatar_url) VALUES
('DrawBot', 'GARTIC_DRAWER', '/static/bots/drawbot.png'),
('WordSmith', 'GARTIC_WRITER', '/static/bots/wordsmith.png'),
('SketchGuess', 'GARTIC_GUESSER', '/static/bots/sketchguess.png');
