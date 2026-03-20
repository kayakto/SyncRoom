-- Общая таблица игровых сессий
CREATE TABLE game_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    game_type   VARCHAR(32) NOT NULL,      -- QUIPLASH, GARTIC_PHONE
    status      VARCHAR(32) NOT NULL DEFAULT 'LOBBY', -- LOBBY, IN_PROGRESS, FINISHED
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_game_sessions_room ON game_sessions(room_id);

-- Участники игры
CREATE TABLE game_players (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id    UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_ready   BOOLEAN NOT NULL DEFAULT FALSE,
    score      INT NOT NULL DEFAULT 0,

    UNIQUE(game_id, user_id)
);

CREATE INDEX idx_game_players_game ON game_players(game_id);

-- Quiplash: промпты
CREATE TABLE quiplash_prompts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id     UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    round       INT NOT NULL,
    text        TEXT NOT NULL,
    time_limit  INT NOT NULL DEFAULT 60
);

CREATE INDEX idx_quiplash_prompts_game ON quiplash_prompts(game_id);

-- Quiplash: ответы игроков
CREATE TABLE quiplash_answers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id  UUID NOT NULL REFERENCES quiplash_prompts(id) ON DELETE CASCADE,
    player_id  UUID NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
    text       TEXT NOT NULL,
    votes      INT NOT NULL DEFAULT 0,

    UNIQUE(prompt_id, player_id)
);

-- Quiplash: голоса
CREATE TABLE quiplash_votes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id  UUID NOT NULL REFERENCES quiplash_prompts(id) ON DELETE CASCADE,
    voter_id   UUID NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
    answer_id  UUID NOT NULL REFERENCES quiplash_answers(id) ON DELETE CASCADE,

    UNIQUE(prompt_id, voter_id)
);

-- Банк промптов
CREATE TABLE prompt_bank (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    text TEXT NOT NULL
);

INSERT INTO prompt_bank (text) VALUES
    ('Что было бы, если бы коты умели говорить?'),
    ('Самый странный подарок на день рождения'),
    ('Чему точно НЕ учат в школе?'),
    ('Худшее название для ресторана'),
    ('Что бы написал ИИ в своём Тиндере?'),
    ('Секретный ингредиент бабушкиного борща'),
    ('Почему опоздал на работу (правдивая версия)'),
    ('О чём думает кот в 3 часа ночи'),
    ('Последнее, что хочешь услышать от стоматолога'),
    ('Альтернативное название для России');

